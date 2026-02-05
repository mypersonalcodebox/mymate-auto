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
import com.google.gson.reflect.TypeToken
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.QuickAction
import com.mymate.auto.data.model.QuickActions
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainAutoScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "MainAutoScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val gson = Gson()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val preferencesManager = PreferencesManager(carContext)
    private val webhookUrl: String
        get() = runBlocking { preferencesManager.getWebhookUrlSync() }
    private val ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
    
    @Volatile
    private var currentResponse: String = "Welkom bij MyMate! Kies een actie of stel een vraag."
    
    private val isLoading = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    
    init {
        try {
            tts = TextToSpeech(carContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                isDestroyed.set(true)
                try {
                    tts?.stop()
                    tts?.shutdown()
                } catch (e: Exception) {
                    Log.e(TAG, "Error shutting down TTS", e)
                }
                tts = null
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
        
        // ========== HOOFDMENU - PRAAT IS DE HOOFDFUNCTIE ==========
        // Android Auto max 6 items!
        
        // 1. 🎤 PRAAT MET MYMATE - DE HOOFDFUNCTIE (prominent!)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 PRAAT MET MYMATE")
                .addText("Start een gesprek met je AI-assistent")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { ConversationAutoScreen(carContext) }
                }
                .build()
        )
        
        // 2. 📍 Parking (save/find location)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📍 Parking")
                .addText("Locatie opslaan of vinden")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { ParkingAutoScreen(carContext) }
                }
                .build()
        )
        
        // 3. ⏰ Herinneringen (reminders)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏰ Herinneringen")
                .addText("Timers en meldingen")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { RemindersAutoScreen(carContext) }
                }
                .build()
        )
        
        // 4. 📝 Notities (memories)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📝 Notities")
                .addText("Opgeslagen herinneringen")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { MemoriesAutoScreen(carContext) }
                }
                .build()
        )
        
        // 5. 📅 Agenda
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📅 Agenda")
                .addText("Bekijk je afspraken")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { AgendaAutoScreen(carContext) }
                }
                .build()
        )
        
        // 6. ⚙️ Instellingen
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⚙️ Instellingen")
                .addText("Opties en configuratie")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { SettingsAutoScreen(carContext) }
                }
                .build()
        )
        
        val templateBuilder = ListTemplate.Builder()
            .setTitle("MyMate")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .setLoading(isLoading.get())
        
        // Action strip with last response
        if (!isLoading.get()) {
            templateBuilder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("📋 Laatste")
                            .setOnClickListener {
                                safeNavigate { ResponseScreen(carContext, currentResponse) }
                            }
                            .build()
                    )
                    .build()
            )
        }
        
        return templateBuilder.build()
    }
    
    private fun safeNavigate(screenProvider: () -> Screen) {
        if (isDestroyed.get()) {
            Log.w(TAG, "Screen destroyed, skipping navigation")
            return
        }
        
        mainHandler.post {
            try {
                if (!isDestroyed.get()) {
                    screenManager.push(screenProvider())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Navigation failed", e)
            }
        }
    }
    
    private fun sendMessage(message: String, quickActionId: String?) {
        if (!isLoading.compareAndSet(false, true)) {
            Log.w(TAG, "Already loading, ignoring request")
            return
        }
        
        safeInvalidate()
        
        val json = gson.toJson(mapOf(
            "message" to message,
            "action" to (quickActionId ?: "chat"),
            "source" to "android_auto"
        ))
        val body = json.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        
        Log.d(TAG, "Sending message: $message")
        
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
                        
                        // Check HTTP status before parsing
                        if (!response.isSuccessful) {
                            Log.e(TAG, "HTTP error ${response.code}: $responseBody")
                            when (response.code) {
                                401 -> "❌ Token onjuist"
                                403 -> "❌ Geen toegang"
                                404 -> "❌ Webhook niet gevonden"
                                500, 502, 503, 504 -> "❌ Server fout (${response.code})"
                                else -> "❌ Fout: HTTP ${response.code}"
                            }
                        } else {
                            val result = gson.fromJson(responseBody, Map::class.java)
                            result["reply"]?.toString() 
                                ?: result["message"]?.toString() 
                                ?: "Geen antwoord"
                        }
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
            if (isDestroyed.get()) {
                Log.w(TAG, "Screen destroyed, ignoring response")
                return@post
            }
            
            currentResponse = responseText
            isLoading.set(false)
            
            if (ttsEnabled && ttsReady && tts != null) {
                try {
                    tts?.speak(currentResponse, TextToSpeech.QUEUE_FLUSH, null, "response")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS error", e)
                }
            }
            
            safeInvalidate()
            safeNavigate { ResponseScreen(carContext, currentResponse) }
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
}

/**
 * Quick Actions Screen - Chat-like interface for voice commands
 * 
 * Features:
 * - Voice input button at top for giving commands
 * - List of available quick actions with descriptions
 * - Command history showing recent exchanges
 * - Conversational feel with TTS responses
 */
class QuickActionsScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "QuickActionsScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val gson = Gson()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val preferencesManager = PreferencesManager(carContext)
    private val gatewayUrl: String
        get() = runBlocking { preferencesManager.getGatewayUrlSync() }
    private val gatewayToken: String
        get() = runBlocking { preferencesManager.getGatewayTokenSync() }
    private val ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
    
    // Command history - stores user commands and bot responses
    data class CommandEntry(
        val userCommand: String,
        val botResponse: String,
        val timestamp: Long,
        val actionId: String?
    )
    
    @Volatile
    private var commandHistory: MutableList<CommandEntry> = mutableListOf()
    
    @Volatile
    private var pendingCommand: String? = null
    
    @Volatile
    private var pendingActionId: String? = null
    
    @Volatile
    private var connectionStatus = "Klaar om te helpen"
    
    private val isLoading = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    private val showingActions = AtomicBoolean(true) // Toggle between actions list and history
    
    private val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale("nl", "NL"))
    
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
        
        // 1. Voice input button at top - primary action
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Geef opdracht")
                .addText(if (isLoading.get()) "Even denken..." else connectionStatus)
                .setOnClickListener {
                    if (!isLoading.get()) {
                        screenManager.push(
                            VoiceInputScreen(carContext, "quick_action") { text ->
                                sendCommand(text, null)
                            }
                        )
                    }
                }
                .build()
        )
        
        // 2. Loading indicator if processing
        if (isLoading.get()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("⏳ Bezig met verwerken...")
                    .addText(pendingCommand?.take(40) ?: "")
                    .build()
            )
        }
        
        // 3. Show recent command history (newest first) - conversational style
        if (commandHistory.isNotEmpty()) {
            // Show last 3 exchanges (Android Auto has item limits)
            commandHistory.takeLast(3).reversed().forEach { entry ->
                val time = timeFormat.format(java.util.Date(entry.timestamp))
                val userPreview = "👤 ${entry.userCommand.take(35)}"
                val botPreview = "🤖 ${entry.botResponse.take(50).replace("\n", " ")}"
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(userPreview)
                        .addText("$botPreview • $time")
                        .setOnClickListener {
                            // Show full response
                            screenManager.push(
                                MessageScreen(carContext, "Laatste antwoord", entry.botResponse) {
                                    screenManager.pop()
                                }
                            )
                        }
                        .build()
                )
            }
        }
        
        // 4. Quick action suggestions - top 3 most used
        val topActions = getSortedQuickActions().take(3)
        if (topActions.isNotEmpty() && !isLoading.get()) {
            topActions.forEach { action ->
                val description = getActionDescription(action)
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("${action.emoji} ${action.title}")
                        .addText(description)
                        .setBrowsable(action.query.isEmpty())
                        .setOnClickListener {
                            handleActionClick(action)
                        }
                        .build()
                )
            }
        }
        
        // 5. "Meer acties" to see full list
        if (!isLoading.get()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("📋 Alle acties bekijken")
                    .addText("Toon alle beschikbare opdrachten")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(AllActionsScreen(carContext) { action ->
                            handleActionClick(action)
                        })
                    }
                    .build()
            )
        }
        
        val templateBuilder = ListTemplate.Builder()
            .setTitle("⚡ Snelle Opdrachten")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setLoading(isLoading.get())
        
        // Clear history button in action strip
        if (commandHistory.isNotEmpty() && !isLoading.get()) {
            templateBuilder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("🗑️ Wis")
                            .setOnClickListener {
                                commandHistory.clear()
                                connectionStatus = "Geschiedenis gewist"
                                safeInvalidate()
                            }
                            .build()
                    )
                    .build()
            )
        }
        
        return templateBuilder.build()
    }
    
    private fun getActionDescription(action: QuickAction): String {
        return when {
            action.query.isNotEmpty() -> "Vraag direct"
            action.id == "quick_note" -> "Spreek je notitie in"
            action.id == "remind_me" -> "Stel een herinnering"
            action.id == "search_info" -> "Zoek iets op"
            action.id == "free_chat" -> "Vraag wat je wilt"
            else -> "Spreek je opdracht"
        }
    }
    
    private fun handleActionClick(action: QuickAction) {
        if (isLoading.get()) return
        
        incrementUsage(action.id)
        
        if (action.query.isNotEmpty()) {
            // Direct query - send immediately
            sendCommand(action.query, action.id)
        } else {
            // Needs voice input
            screenManager.push(
                VoiceInputScreen(carContext, action.id) { message ->
                    sendCommand(message, action.id)
                }
            )
        }
    }
    
    private fun getSortedQuickActions(): List<QuickAction> {
        return try {
            val usageMap: Map<String, Int> = runBlocking { preferencesManager.getActionUsage() }
            val lastUsedMap: Map<String, Long> = runBlocking { preferencesManager.getActionLastUsed() }
            
            QuickActions.mainActions.map { action ->
                action.copy(
                    usageCount = usageMap[action.id] ?: 0,
                    lastUsed = lastUsedMap[action.id] ?: 0
                )
            }.sortedWith(
                compareByDescending<QuickAction> { it.usageCount }
                    .thenByDescending { it.lastUsed }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sorted actions", e)
            QuickActions.mainActions
        }
    }
    
    private fun incrementUsage(actionId: String) {
        try {
            runBlocking { preferencesManager.incrementActionUsage(actionId) }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing usage", e)
        }
    }
    
    private fun sendCommand(command: String, actionId: String?) {
        if (!isLoading.compareAndSet(false, true)) {
            Log.w(TAG, "Already loading, ignoring request")
            return
        }
        
        pendingCommand = command
        pendingActionId = actionId
        connectionStatus = "Versturen..."
        safeInvalidate()
        
        // Build request body
        val requestBody = mapOf(
            "message" to command,
            "action" to (actionId ?: "quick_action"),
            "source" to "android_auto_quick_actions"
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
        
        Log.d(TAG, "Sending command: $command")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
                handleResponse("❌ Verbindingsfout: ${e.localizedMessage ?: "Onbekende fout"}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Got response: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        401 -> "❌ Token onjuist - controleer instellingen"
                        403 -> "❌ Geen toegang"
                        404 -> "❌ Server niet gevonden"
                        500, 502, 503, 504 -> "❌ Server fout - probeer later"
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
            
            // Add to command history
            val command = pendingCommand ?: "Opdracht"
            commandHistory.add(CommandEntry(
                userCommand = command,
                botResponse = responseText,
                timestamp = System.currentTimeMillis(),
                actionId = pendingActionId
            ))
            
            // Keep history limited to last 10 entries
            while (commandHistory.size > 10) {
                commandHistory.removeAt(0)
            }
            
            pendingCommand = null
            pendingActionId = null
            isLoading.set(false)
            connectionStatus = "Klaar om te helpen"
            
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
 * Screen showing all available quick actions organized by category
 */
class AllActionsScreen(
    carContext: CarContext,
    private val onActionSelected: (QuickAction) -> Unit
) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Group actions by category with descriptions
        val actionsByCategory = QuickActions.mainActions.groupBy { it.category }
        
        // Show all actions with descriptions (Android Auto limit is 6)
        QuickActions.mainActions.take(6).forEach { action ->
            val description = when {
                action.query.isNotEmpty() -> "Tik om direct te vragen"
                else -> "Spreek je opdracht in"
            }
            
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${action.emoji} ${action.title}")
                    .addText(description)
                    .setOnClickListener {
                        screenManager.pop()
                        onActionSelected(action)
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("📋 Alle Opdrachten")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
