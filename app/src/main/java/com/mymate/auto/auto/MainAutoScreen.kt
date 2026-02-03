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

class MainAutoScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "MainAutoScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    private val webhookUrl: String
        get() = prefs.getString("webhook_url", "http://100.124.24.27:18791/auto") ?: "http://100.124.24.27:18791/auto"
    private val ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
    
    private var currentResponse: String = "Welkom bij MyMate! Kies een actie of stel een vraag."
    private var isLoading = false
    
    init {
        tts = TextToSpeech(carContext, this)
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                tts?.stop()
                tts?.shutdown()
                tts = null
            }
        })
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("nl", "NL"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }
    
    override fun onGetTemplate(): Template {
        val sortedActions = getSortedQuickActions()
        
        val listBuilder = ItemList.Builder()
        
        sortedActions.take(15).forEach { action ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${action.emoji} ${action.title}")
                    .setBrowsable(action.query.isEmpty())
                    .setOnClickListener {
                        if (action.query.isNotEmpty()) {
                            incrementUsage(action.id)
                            sendMessage(action.query, action.id)
                        } else {
                            screenManager.push(VoiceInputScreen(carContext) { message ->
                                sendMessage(message, action.id)
                            })
                        }
                    }
                    .build()
            )
        }
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Spreek vrij")
                .setOnClickListener {
                    screenManager.push(VoiceInputScreen(carContext) { message ->
                        sendMessage(message, null)
                    })
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("MyMate")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Laatste")
                            .setOnClickListener {
                                screenManager.push(ResponseScreen(carContext, currentResponse))
                            }
                            .build()
                    )
                    .build()
            )
            .setLoading(isLoading)
            .build()
    }
    
    private fun getSortedQuickActions(): List<QuickAction> {
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
        
        return QuickActions.defaultActions.map { action ->
            action.copy(
                usageCount = usageMap[action.id] ?: 0,
                lastUsed = lastUsedMap[action.id] ?: 0
            )
        }.sortedWith(
            compareByDescending<QuickAction> { it.usageCount }
                .thenByDescending { it.lastUsed }
        )
    }
    
    private fun incrementUsage(actionId: String) {
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
    }
    
    private fun sendMessage(message: String, quickActionId: String?) {
        isLoading = true
        invalidate()
        
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
                
                // Run on main thread!
                mainHandler.post {
                    currentResponse = "❌ Verbindingsfout: ${e.localizedMessage}"
                    isLoading = false
                    invalidate()
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Got response: ${response.code}")
                
                val responseText = try {
                    response.body?.string()?.let { responseBody ->
                        val result = gson.fromJson(responseBody, Map::class.java)
                        result["reply"]?.toString() 
                            ?: result["message"]?.toString() 
                            ?: "Geen antwoord"
                    } ?: "Leeg antwoord"
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    "Fout bij verwerken antwoord"
                }
                
                // Run everything on main thread!
                mainHandler.post {
                    currentResponse = responseText
                    isLoading = false
                    
                    // Speak response if TTS is enabled
                    if (ttsEnabled && ttsReady) {
                        tts?.speak(currentResponse, TextToSpeech.QUEUE_FLUSH, null, "response")
                    }
                    
                    // Update UI first
                    invalidate()
                    
                    // Then push response screen
                    try {
                        screenManager.push(ResponseScreen(carContext, currentResponse))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to push response screen", e)
                    }
                }
            }
        })
    }
}
