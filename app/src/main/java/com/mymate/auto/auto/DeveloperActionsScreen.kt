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
import com.mymate.auto.data.model.QuickActions
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Developer Actions Screen - Chat-like interface for dev commands
 * 
 * Shows available dev actions as context, lets you give voice commands,
 * and keeps command history visible like a chat.
 */
class DeveloperActionsScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "DeveloperActionsScreen"
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
    
    private val preferencesManager = PreferencesManager(carContext)
    private val gatewayUrl: String
        get() = runBlocking { preferencesManager.getGatewayUrlSync() }
    private val gatewayToken: String
        get() = runBlocking { preferencesManager.getGatewayTokenSync() }
    private val ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
    
    private val isLoading = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    
    // Chat history for this dev session
    @Volatile
    private var commandHistory: MutableList<ConversationMessage> = mutableListOf()
    
    @Volatile
    private var connectionStatus = "Klaar voor dev opdrachten"
    
    private val sessionKey = "agent:main:mymate:developer:auto"
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("nl", "NL"))
    
    // Track which quick actions have been used this session for smart suggestions
    private val usedActions = mutableSetOf<String>()
    
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
        
        // === 🎤 Voice command button (main action) ===
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Dev opdracht")
                .addText(if (isLoading.get()) "Even denken..." else connectionStatus)
                .setOnClickListener {
                    if (!isLoading.get()) {
                        screenManager.push(
                            VoiceInputScreen(carContext, "dev_command") { text ->
                                sendDevCommand(text, "dev_chat")
                            }
                        )
                    }
                }
                .build()
        )
        
        // === Loading indicator ===
        if (isLoading.get()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("⏳ Even denken...")
                    .build()
            )
        }
        
        // === Quick action shortcuts (most useful ones) ===
        if (commandHistory.isEmpty()) {
            // Show suggestions when no history yet
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("── Snelle acties ──")
                    .build()
            )
            
            // Top 4 dev actions as quick buttons
            val topActions = listOf(
                QuickActions.developerActions.find { it.id == "task_status" },
                QuickActions.developerActions.find { it.id == "code_review" },
                QuickActions.developerActions.find { it.id == "deploy_status" },
                QuickActions.developerActions.find { it.id == "start_task" }
            ).filterNotNull()
            
            topActions.forEach { action ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("${action.emoji} ${action.title}")
                        .addText(if (action.query.isEmpty()) "Spreek opdracht in" else "Tik om uit te voeren")
                        .setOnClickListener {
                            handleQuickAction(action.id, action.query, action.title)
                        }
                        .build()
                )
            }
        }
        
        // === Command history (chat-style) ===
        if (commandHistory.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("── Geschiedenis ──")
                    .build()
            )
            
            // Show last 5 messages, newest first
            commandHistory.takeLast(5).reversed().forEach { message ->
                val emoji = if (message.isFromUser) "👤" else "🤖"
                val time = timeFormat.format(Date(message.timestamp))
                val preview = message.content.take(50).replace("\n", " ")
                val suffix = if (message.content.length > 50) "..." else ""
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("$emoji $preview$suffix")
                        .addText(time)
                        .setOnClickListener {
                            // Show full message in detail screen
                            screenManager.push(
                                ResponseScreen(carContext, message.content)
                            )
                        }
                        .build()
                )
            }
        }
        
        // === More actions button ===
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📋 Alle dev acties")
                .addText("${QuickActions.developerActions.size} beschikbaar")
                .setOnClickListener {
                    screenManager.push(DevActionsListScreen(carContext) { actionId, query, title ->
                        handleQuickAction(actionId, query, title)
                    })
                }
                .build()
        )
        
        // === Clear history button ===
        if (commandHistory.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🗑️ Geschiedenis wissen")
                    .setOnClickListener {
                        commandHistory.clear()
                        usedActions.clear()
                        connectionStatus = "Klaar voor dev opdrachten"
                        invalidate()
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("🛠️ Developer")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setLoading(isLoading.get())
            .build()
    }
    
    private fun handleQuickAction(actionId: String, query: String, title: String) {
        usedActions.add(actionId)
        
        if (query.isNotEmpty()) {
            // Direct action with predefined query
            sendDevCommand(query, actionId)
        } else {
            // Needs voice input
            screenManager.push(
                VoiceInputScreen(carContext, actionId) { text ->
                    sendDevCommand(text, actionId)
                }
            )
        }
    }
    
    private fun sendDevCommand(command: String, actionId: String) {
        if (!isLoading.compareAndSet(false, true)) {
            Log.w(TAG, "Already loading, ignoring request")
            return
        }
        
        // Add user command to history
        commandHistory.add(ConversationMessage(
            id = System.currentTimeMillis(),
            content = command,
            isFromUser = true,
            timestamp = System.currentTimeMillis(),
            topic = actionId
        ))
        
        connectionStatus = "Versturen..."
        safeInvalidate()
        
        // Build context from recent commands (last 6)
        val contextMessages = commandHistory.takeLast(6).map { msg ->
            mapOf(
                "role" to if (msg.isFromUser) "user" else "assistant",
                "content" to msg.content
            )
        }
        
        val requestBody = mapOf(
            "message" to command,
            "sessionKey" to sessionKey,
            "context" to contextMessages,
            "action" to actionId,
            "category" to "developer",
            "source" to "android_auto_developer"
        )
        
        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())
        
        // Build HTTP URL from gateway URL
        val httpUrl = try {
            val uri = java.net.URI(gatewayUrl)
            val scheme = when (uri.scheme) {
                "ws" -> "http"
                "wss" -> "https"
                else -> uri.scheme
            }
            val port = if (uri.port > 0) uri.port else 18789
            "$scheme://${uri.host}:$port/hooks/agent"
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing gateway URL", e)
            gatewayUrl
        }
        
        val request = Request.Builder()
            .url(httpUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $gatewayToken")
            .build()
        
        Log.d(TAG, "Sending dev command: $command (action: $actionId)")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
                handleResponse("❌ Verbindingsfout: ${e.localizedMessage ?: "Onbekende fout"}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Got response: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        401 -> "❌ Authenticatie mislukt - check je token"
                        403 -> "❌ Geen toegang"
                        404 -> "❌ Gateway niet gevonden"
                        500, 502, 503 -> "❌ Server fout - probeer later"
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
            
            isLoading.set(false)
            connectionStatus = "Klaar voor dev opdrachten"
            
            // Add response to history
            commandHistory.add(ConversationMessage(
                id = System.currentTimeMillis(),
                content = responseText,
                isFromUser = false,
                timestamp = System.currentTimeMillis(),
                topic = "response"
            ))
            
            // Speak response if TTS enabled
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

/**
 * Sub-screen showing all developer actions in a list
 */
class DevActionsListScreen(
    carContext: CarContext,
    private val onActionSelected: (actionId: String, query: String, title: String) -> Unit
) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        QuickActions.developerActions.forEach { action ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${action.emoji} ${action.title}")
                    .addText(if (action.query.isEmpty()) "🎤 Spreek opdracht in" else "Tik om uit te voeren")
                    .setOnClickListener {
                        screenManager.pop()
                        onActionSelected(action.id, action.query, action.title)
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("📋 Alle dev acties")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
