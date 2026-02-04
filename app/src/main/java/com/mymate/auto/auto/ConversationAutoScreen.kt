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
import com.google.gson.Gson
import com.mymate.auto.data.model.ConversationMessage
import com.mymate.auto.data.remote.OpenClawWebSocket
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ConversationAutoScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "ConversationAutoScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    private val gatewayHost: String
        get() = prefs.getString("gateway_host", "100.124.24.27") ?: "100.124.24.27"
    private val gatewayPort: Int
        get() = prefs.getInt("gateway_port", 18789)
    private val authToken: String
        get() = prefs.getString("auth_token", "969802d413a94e7e4950fc6d12c441ea5b316b65df1fb7cb") 
            ?: "969802d413a94e7e4950fc6d12c441ea5b316b65df1fb7cb"
    private val ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
    
    private var webSocket: OpenClawWebSocket? = null
    private val isConnected = AtomicBoolean(false)
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
        
        connectWebSocket()
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
    
    private fun connectWebSocket() {
        scope.launch {
            try {
                val wsUrl = "ws://$gatewayHost:$gatewayPort/ws"
                Log.d(TAG, "Connecting to WebSocket: $wsUrl")
                
                webSocket = OpenClawWebSocket(
                    url = wsUrl,
                    authToken = authToken,
                    onConnected = {
                        Log.d(TAG, "WebSocket connected")
                        isConnected.set(true)
                        connectionStatus = "Verbonden ✅"
                        mainHandler.post { safeInvalidate() }
                    },
                    onDisconnected = { reason ->
                        Log.d(TAG, "WebSocket disconnected: $reason")
                        isConnected.set(false)
                        connectionStatus = "Niet verbonden ❌"
                        mainHandler.post { safeInvalidate() }
                    },
                    onMessage = { response ->
                        handleWebSocketResponse(response)
                    },
                    onError = { error ->
                        Log.e(TAG, "WebSocket error: $error")
                        connectionStatus = "Fout: $error"
                        mainHandler.post { safeInvalidate() }
                    }
                )
                
                webSocket?.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect WebSocket", e)
                connectionStatus = "Verbinding mislukt"
                mainHandler.post { safeInvalidate() }
            }
        }
    }
    
    private fun handleWebSocketResponse(response: String) {
        mainHandler.post {
            if (isDestroyed.get()) return@post
            
            try {
                val responseMap = gson.fromJson(response, Map::class.java)
                val reply = responseMap["reply"]?.toString() 
                    ?: responseMap["message"]?.toString()
                    ?: responseMap["text"]?.toString()
                
                if (reply != null) {
                    lastResponse = reply
                    isLoading.set(false)
                    
                    // Add to messages
                    messages.add(ConversationMessage(
                        id = System.currentTimeMillis(),
                        content = reply,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis(),
                        topic = "chat"
                    ))
                    
                    // Speak if TTS enabled
                    if (ttsEnabled && ttsReady && tts != null) {
                        try {
                            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "response")
                        } catch (e: Exception) {
                            Log.e(TAG, "TTS error", e)
                        }
                    }
                    
                    safeInvalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing response", e)
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Connection status & new message button
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Nieuw bericht")
                .addText(connectionStatus)
                .setOnClickListener {
                    if (isConnected.get()) {
                        screenManager.push(
                            VoiceInputScreen(carContext, "conversation") { message ->
                                sendMessage(message)
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
                    .setTitle("Begin een gesprek!")
                    .addText("Tik op 'Nieuw bericht' om te starten")
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
        
        // Reconnect button if disconnected
        if (!isConnected.get()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🔄 Opnieuw verbinden")
                    .setOnClickListener {
                        connectionStatus = "Verbinden..."
                        invalidate()
                        connectWebSocket()
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("💬 Gesprek")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun sendMessage(message: String) {
        if (!isConnected.get()) {
            Log.w(TAG, "Not connected, can't send message")
            return
        }
        
        isLoading.set(true)
        
        // Add user message
        messages.add(ConversationMessage(
            id = System.currentTimeMillis(),
            content = message,
            isFromUser = true,
            timestamp = System.currentTimeMillis(),
            topic = "chat"
        ))
        
        safeInvalidate()
        
        scope.launch {
            try {
                webSocket?.sendMessage(message, sessionKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                mainHandler.post {
                    isLoading.set(false)
                    safeInvalidate()
                }
            }
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
            webSocket?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket", e)
        }
        
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
        super.onDestroy(owner)
    }
}
