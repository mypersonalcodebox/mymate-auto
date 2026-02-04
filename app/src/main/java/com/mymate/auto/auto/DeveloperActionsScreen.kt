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

class DeveloperActionsScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "DeveloperActionsScreen"
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
        val sortedActions = getSortedDeveloperActions()
        
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
            .setTitle("🛠️ Developer")
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
    
    private fun getSortedDeveloperActions(): List<QuickAction> {
        return try {
            val usageMap: Map<String, Int> = runBlocking { preferencesManager.getActionUsage() }
            val lastUsedMap: Map<String, Long> = runBlocking { preferencesManager.getActionLastUsed() }
            
            QuickActions.developerActions.map { action ->
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
            QuickActions.developerActions
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
        
        safeInvalidate()
        
        val json = gson.toJson(mapOf(
            "message" to message,
            "action" to (quickActionId ?: "dev_chat"),
            "source" to "android_auto",
            "category" to "developer"
        ))
        val body = json.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        
        Log.d(TAG, "Sending dev message: $message")
        
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
