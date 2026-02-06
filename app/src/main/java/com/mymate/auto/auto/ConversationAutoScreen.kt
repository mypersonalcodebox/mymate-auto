package com.mymate.auto.auto

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ConversationMessage
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Conversation screen for Android Auto - uses WebSocket for real-time responses
 */
class ConversationAutoScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "ConversationAutoScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Use shared WebSocket client for real-time responses
    private val agentClient = AutoAgentClient.getInstance(carContext)
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val preferencesManager = PreferencesManager(carContext)
    private val ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
    
    private val isLoading = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    
    @Volatile
    private var messages: MutableList<ConversationMessage> = mutableListOf()
    
    @Volatile
    private var connectionStatus = "Verbinden..."
    
    @Volatile
    private var lastResponse: String? = null
    
    private val sessionKey = "agent:main:mymate:conversation:auto"
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("nl", "NL"))
    
    init {
        try {
            tts = TextToSpeech(carContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cleanup()
            }
        })
        
        // Monitor connection state
        scope.launch {
            agentClient.connectionState.collect { state ->
                mainHandler.post {
                    connectionStatus = agentClient.getStatusText()
                    if (!isDestroyed.get()) {
                        safeInvalidate()
                    }
                }
            }
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val result = tts?.setLanguage(Locale("nl", "NL"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } catch (e: Exception) {
                Log.e(TAG, "Error setting TTS language", e)
                ttsReady = false
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // New message button
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Nieuw bericht")
                .addText(if (isLoading.get()) "Even denken..." else connectionStatus)
                .setOnClickListener {
                    if (!isLoading.get()) {
                        screenManager.push(
                            VoiceInputScreen(carContext, "conversation") { text ->
                                sendMessage(text)
                            }
                        )
                    }
                }
                .build()
        )
        
        // Loading indicator
        if (isLoading.get()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("⏳ Even denken...")
                    .build()
            )
        }
        
        // Show last few messages (reversed - newest first)
        if (messages.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("👋 Welkom!")
                    .addText("Tik hierboven om een gesprek te starten")
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("💡 Tip")
                    .addText("Ik onthoud de context van ons gesprek")
                    .build()
            )
        } else {
            messages.takeLast(6).reversed().forEach { message ->
                val emoji = if (message.isFromUser) "👤" else "🤖"
                val time = timeFormat.format(Date(message.timestamp))
                val preview = message.content.take(60).replace("\n", " ")
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("$emoji $preview")
                        .addText(time)
                        .setOnClickListener {
                            // Show full message
                            screenManager.push(
                                MessageScreen(carContext, 
                                    if (message.isFromUser) "Jouw bericht" else "MyMate",
                                    message.content
                                ) {
                                    screenManager.pop()
                                }
                            )
                        }
                        .build()
                )
            }
        }
        
        // Clear conversation button
        if (messages.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🗑️ Gesprek wissen")
                    .setOnClickListener {
                        messages.clear()
                        invalidate()
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("💬 Gesprek")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setLoading(isLoading.get())
            .build()
    }
    
    private fun sendMessage(message: String) {
        if (!isLoading.compareAndSet(false, true)) {
            Log.w(TAG, "Already loading, ignoring request")
            return
        }
        
        // Add user message
        messages.add(ConversationMessage(
            id = System.currentTimeMillis(),
            content = message,
            isFromUser = true,
            timestamp = System.currentTimeMillis(),
            topic = "chat"
        ))
        
        connectionStatus = "Versturen..."
        safeInvalidate()
        
        Log.d(TAG, "Sending conversation message via WebSocket: $message")
        
        // Send via WebSocket for real-time response
        scope.launch {
            val result = agentClient.sendMessage(message, sessionKey)
            
            result.fold(
                onSuccess = { reply ->
                    handleResponse(reply)
                },
                onFailure = { error ->
                    Log.e(TAG, "Request failed", error)
                    val errorMsg = when {
                        error.message?.contains("timeout", ignoreCase = true) == true ->
                            "❌ Timeout - geen antwoord ontvangen"
                        error.message?.contains("token", ignoreCase = true) == true ->
                            "❌ Authenticatie mislukt - controleer je token"
                        error.message?.contains("verbonden", ignoreCase = true) == true ->
                            "❌ ${error.message}"
                        else ->
                            "❌ Fout: ${error.localizedMessage ?: "Onbekende fout"}"
                    }
                    handleResponse(errorMsg)
                }
            )
        }
    }
    
    private fun handleResponse(responseText: String) {
        mainHandler.post {
            if (isDestroyed.get()) return@post
            
            lastResponse = responseText
            isLoading.set(false)
            connectionStatus = agentClient.getStatusText()
            
            // Add assistant message
            messages.add(ConversationMessage(
                id = System.currentTimeMillis(),
                content = responseText,
                isFromUser = false,
                timestamp = System.currentTimeMillis(),
                topic = "chat"
            ))
            
            // Speak if TTS enabled
            if (ttsEnabled && ttsReady && tts != null) {
                try {
                    tts?.speak(responseText, TextToSpeech.QUEUE_FLUSH, null, "response")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS error", e)
                }
            }
            
            safeInvalidate()
        }
    }
    
    private fun safeInvalidate() {
        if (isDestroyed.get()) return
        
        mainHandler.post {
            try {
                if (!isDestroyed.get()) {
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Invalidate failed", e)
            }
        }
    }
    
    private fun cleanup() {
        isDestroyed.set(true)
        scope.cancel()
        
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
    }
}
