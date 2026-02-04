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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ConversationAutoScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "ConversationAutoScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    private val gatewayHost: String
        get() = prefs.getString("gateway_host", "") ?: ""
    private val gatewayPort: Int
        get() = prefs.getInt("gateway_port", 18789)
    private val authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
    private val ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
    
    private val isLoading = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    
    @Volatile
    private var messages: MutableList<ConversationMessage> = mutableListOf()
    
    @Volatile
    private var connectionStatus = "Gereed"
    
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
        
        // Build conversation context (last 10 messages)
        val contextMessages = messages.takeLast(10).map { msg ->
            mapOf(
                "role" to if (msg.isFromUser) "user" else "assistant",
                "content" to msg.content
            )
        }
        
        val requestBody = mapOf(
            "message" to message,
            "sessionKey" to sessionKey,
            "context" to contextMessages,
            "source" to "android_auto_conversation"
        )
        
        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())
        
        val url = "http://$gatewayHost:18791/auto"
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $authToken")
            .build()
        
        Log.d(TAG, "Sending conversation message: $message")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
                handleResponse("❌ Verbindingsfout: ${e.localizedMessage ?: "Onbekende fout"}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Got response: ${response.code}")
                
                val responseText = try {
                    response.body?.use { body ->
                        val responseBody = body.string()
                        val result = gson.fromJson(responseBody, Map::class.java)
                        result["reply"]?.toString() 
                            ?: result["message"]?.toString()
                            ?: result["text"]?.toString()
                            ?: "Geen antwoord"
                    } ?: "Leeg antwoord"
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    "Fout bij verwerken antwoord"
                }
                
                handleResponse(responseText)
            }
        })
    }
    
    private fun handleResponse(responseText: String) {
        mainHandler.post {
            if (isDestroyed.get()) return@post
            
            lastResponse = responseText
            isLoading.set(false)
            connectionStatus = "Gereed"
            
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
        
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
    }
}
