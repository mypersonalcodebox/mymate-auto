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
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ConversationMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
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
    
    // Coroutine scope for async operations - canceled in cleanup()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val preferencesManager = PreferencesManager(carContext)
    
    // Cached values - loaded asynchronously at init, refreshed before each request
    @Volatile private var cachedGatewayUrl: String = ""
    @Volatile private var cachedGatewayToken: String = ""
    @Volatile private var cachedTtsEnabled: Boolean = false
    private val prefsLoaded = AtomicBoolean(false)
    
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
        // Load preferences asynchronously to avoid blocking main thread
        loadPreferences()
        
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
    
    private fun loadPreferences() {
        scope.launch {
            try {
                cachedGatewayUrl = preferencesManager.getGatewayUrlSync()
                cachedGatewayToken = preferencesManager.getGatewayTokenSync()
                cachedTtsEnabled = preferencesManager.getTtsEnabledSync()
                prefsLoaded.set(true)
                Log.d(TAG, "Preferences loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load preferences", e)
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
        
        // Refresh preferences and send on background thread to avoid blocking main thread
        scope.launch {
            try {
                // Refresh cached values before sending
                cachedGatewayUrl = preferencesManager.getGatewayUrlSync()
                cachedGatewayToken = preferencesManager.getGatewayTokenSync()
                cachedTtsEnabled = preferencesManager.getTtsEnabledSync()
                
                // Validate we have required config
                if (cachedGatewayUrl.isBlank()) {
                    handleResponse("❌ Gateway URL niet ingesteld - ga naar Instellingen")
                    return@launch
                }
                
                if (cachedGatewayToken.isBlank()) {
                    handleResponse("❌ Gateway token niet ingesteld - ga naar Instellingen")
                    return@launch
                }
                
                sendMessageInternal(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing message", e)
                handleResponse("❌ Fout bij versturen: ${e.localizedMessage ?: "Onbekende fout"}")
            }
        }
    }
    
    private fun sendMessageInternal(message: String) {
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
        
        // Build HTTP URL from gateway URL (convert ws:// to http://, wss:// to https://)
        val httpUrl = try {
            val url = cachedGatewayUrl
            if (url.isBlank()) {
                handleResponse("❌ Gateway URL niet geconfigureerd")
                return
            }
            val uri = java.net.URI(url)
            val scheme = when (uri.scheme) {
                "ws" -> "http"
                "wss" -> "https"
                else -> uri.scheme ?: "http"
            }
            val host = uri.host
            if (host.isNullOrBlank()) {
                handleResponse("❌ Ongeldige gateway URL")
                return
            }
            val port = if (uri.port > 0) uri.port else 18789
            "$scheme://$host:$port/hooks/agent"
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing gateway URL", e)
            handleResponse("❌ Ongeldige gateway URL: ${e.localizedMessage}")
            return
        }
        
        val token = cachedGatewayToken
        if (token.isBlank()) {
            handleResponse("❌ Gateway token niet geconfigureerd")
            return
        }
        
        val request = Request.Builder()
            .url(httpUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        
        Log.d(TAG, "Sending conversation message: $message")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
                handleResponse("❌ Verbindingsfout: ${e.localizedMessage ?: "Onbekende fout"}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Got response: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        401 -> "❌ Authenticatie mislukt - controleer je token in Instellingen"
                        403 -> "❌ Geen toegang - controleer je configuratie"
                        404 -> "❌ Gateway niet gevonden"
                        500, 502, 503 -> "❌ Server fout - probeer later opnieuw"
                        else -> "❌ Fout: HTTP ${response.code}"
                    }
                    handleResponse(errorMsg)
                    return
                }
                
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
            
            // Speak if TTS enabled (using cached value)
            if (cachedTtsEnabled && ttsReady && tts != null) {
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
        
        // Cancel any pending coroutines
        try {
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling coroutine scope", e)
        }
        
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
    }
}
