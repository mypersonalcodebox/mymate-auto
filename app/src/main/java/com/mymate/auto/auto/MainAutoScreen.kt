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
import com.mymate.auto.data.model.QuickAction
import com.mymate.auto.data.model.QuickActions
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
    
    // Use a single shared OkHttp client
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val gson = Gson()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    private val webhookUrl: String
        get() = prefs.getString("webhook_url", "http://100.124.24.27:18791/auto") ?: "http://100.124.24.27:18791/auto"
    private val ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
    
    @Volatile
    private var currentResponse: String = "Welkom bij MyMate! Kies een actie of stel een vraag."
    
    // Use AtomicBoolean to prevent race conditions
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
        
        // Normale quick actions (max 12 om ruimte te laten voor Developer + Spreek vrij)
        sortedActions.take(12).forEach { action ->
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
        
        // === DEVELOPER SUBMENU ===
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🛠️ Developer")
                .setBrowsable(true)
                .setOnClickListener {
                    safeNavigate { DeveloperActionsScreen(carContext) }
                }
                .build()
        )
        
        // === VRIJE VRAAG ===
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Spreek vrij")
                .setOnClickListener {
                    safeNavigate {
                        VoiceInputScreen(carContext, null) { message ->
                            sendMessage(message, null)
                        }
                    }
                }
                .build()
        )
        
        val templateBuilder = ListTemplate.Builder()
            .setTitle("MyMate")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .setLoading(isLoading.get())
        
        // Only add action strip if not loading
        if (!isLoading.get()) {
            templateBuilder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Laatste")
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
    
    private fun handleActionClick(action: QuickAction) {
        if (action.query.isNotEmpty()) {
            incrementUsage(action.id)
            sendMessage(action.query, action.id)
        } else {
            safeNavigate {
                VoiceInputScreen(carContext, action.id) { message ->
                    incrementUsage(action.id)
                    sendMessage(message, action.id)
                }
            }
        }
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
    
    private fun getSortedQuickActions(): List<QuickAction> {
        return try {
            val usageJson = prefs.getString("action_usage", "{}") ?: "{}"
            val lastUsedJson = prefs.getString("action_last_used", "{}") ?: "{}"
            
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val longType = object : TypeToken<Map<String, Long>>() {}.type
            
            val usageMap: Map<String, Int> = try {
                gson.fromJson(usageJson, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
            
            val lastUsedMap: Map<String, Long> = try {
                gson.fromJson(lastUsedJson, longType) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
            
            // Alleen mainActions, NIET developerActions
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
            val usageJson = prefs.getString("action_usage", "{}") ?: "{}"
            val lastUsedJson = prefs.getString("action_last_used", "{}") ?: "{}"
            
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            val longType = object : TypeToken<MutableMap<String, Long>>() {}.type
            
            val usageMap: MutableMap<String, Int> = try {
                gson.fromJson(usageJson, type) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }
            
            val lastUsedMap: MutableMap<String, Long> = try {
                gson.fromJson(lastUsedJson, longType) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }
            
            usageMap[actionId] = (usageMap[actionId] ?: 0) + 1
            lastUsedMap[actionId] = System.currentTimeMillis()
            
            prefs.edit()
                .putString("action_usage", gson.toJson(usageMap))
                .putString("action_last_used", gson.toJson(lastUsedMap))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing usage", e)
        }
    }
    
    private fun sendMessage(message: String, quickActionId: String?) {
        // Prevent duplicate requests
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
        // Always run on main thread
        mainHandler.post {
            if (isDestroyed.get()) {
                Log.w(TAG, "Screen destroyed, ignoring response")
                return@post
            }
            
            currentResponse = responseText
            isLoading.set(false)
            
            // Speak response if TTS is enabled
            if (ttsEnabled && ttsReady && tts != null) {
                try {
                    tts?.speak(currentResponse, TextToSpeech.QUEUE_FLUSH, null, "response")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS error", e)
                }
            }
            
            // Update UI
            safeInvalidate()
            
            // Navigate to response screen
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
