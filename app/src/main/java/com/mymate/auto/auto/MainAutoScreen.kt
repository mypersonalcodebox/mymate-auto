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
        
        // ========== HOOFDFUNCTIES ==========
        
        // 1. Gesprek modus (Conversation)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("💬 Gesprek")
                .addText("Chat met context")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { ConversationAutoScreen(carContext) }
                }
                .build()
        )
        
        // 2. Parking
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🅿️ Parking")
                .addText("Parkeerlocatie opslaan/vinden")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { ParkingAutoScreen(carContext) }
                }
                .build()
        )
        
        // 3. Memories
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📝 Memories")
                .addText("Notities en herinneringen")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { MemoriesAutoScreen(carContext) }
                }
                .build()
        )
        
        // 4. Reminders
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
        
        // ========== QUICK ACTIONS ==========
        
        // 5. Quick Actions menu
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⚡ Quick Actions")
                .addText("Snelle commando's")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { QuickActionsScreen(carContext) }
                }
                .build()
        )
        
        // 6. Vrije vraag
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Stel een vraag")
                .addText("Spreek vrij")
                .setOnClickListener {
                    safeNavigate {
                        VoiceInputScreen(carContext, null) { message ->
                            sendMessage(message, null)
                        }
                    }
                }
                .build()
        )
        
        // ========== OVERIG ==========
        
        // 7. Developer menu
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🛠️ Developer")
                .addText("Taken en projecten")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { DeveloperActionsScreen(carContext) }
                }
                .build()
        )
        
        // 8. Settings
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⚙️ Instellingen")
                .addText("TTS, verbinding, etc.")
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
                        val result = gson.fromJson(responseBody, Map::class.java)
                        result["reply"]?.toString() 
                            ?: result["message"]?.toString() 
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

// Quick Actions submenu
class QuickActionsScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "QuickActionsScreen"
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
    private var currentResponse: String = ""
    
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
        val sortedActions = getSortedQuickActions()
        
        val listBuilder = ItemList.Builder()
        
        sortedActions.forEach { action ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${action.emoji} ${action.title}")
                    .setBrowsable(action.query.isEmpty())
                    .setOnClickListener {
                        handleActionClick(action)
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("⚡ Quick Actions")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setLoading(isLoading.get())
            .build()
    }
    
    private fun handleActionClick(action: QuickAction) {
        if (action.query.isNotEmpty()) {
            incrementUsage(action.id)
            sendMessage(action.query, action.id)
        } else {
            if (!isDestroyed.get()) {
                mainHandler.post {
                    screenManager.push(
                        VoiceInputScreen(carContext, action.id) { message ->
                            incrementUsage(action.id)
                            sendMessage(message, action.id)
                        }
                    )
                }
            }
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
    
    private fun sendMessage(message: String, quickActionId: String?) {
        if (!isLoading.compareAndSet(false, true)) {
            Log.w(TAG, "Already loading, ignoring request")
            return
        }
        
        try {
            invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Invalidate failed", e)
        }
        
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
                        val result = gson.fromJson(responseBody, Map::class.java)
                        result["reply"]?.toString() 
                            ?: result["message"]?.toString() 
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
            
            try {
                invalidate()
            } catch (e: Exception) {
                Log.e(TAG, "Invalidate failed", e)
            }
            
            if (!isDestroyed.get()) {
                mainHandler.post {
                    try {
                        screenManager.push(ResponseScreen(carContext, currentResponse))
                    } catch (e: Exception) {
                        Log.e(TAG, "Navigation failed", e)
                    }
                }
            }
        }
    }
}
