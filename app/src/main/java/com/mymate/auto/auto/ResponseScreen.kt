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
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ResponseScreen(
    carContext: CarContext,
    private val response: String
) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "ResponseScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val isDestroyed = AtomicBoolean(false)
    
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    private val ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
    
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
        // Truncate very long responses for display
        val displayResponse = if (response.length > 500) {
            response.take(500) + "..."
        } else {
            response
        }
        
        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Antwoord")
                    .addText(displayResponse)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🔊 Voorlezen")
                    .setOnClickListener {
                        speakResponse()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⬅️ Terug")
                    .setOnClickListener {
                        safeGoBack()
                    }
                    .build()
            )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("MyMate")
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun speakResponse() {
        if (isDestroyed.get()) return
        
        if (ttsReady && tts != null) {
            try {
                tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "response")
            } catch (e: Exception) {
                Log.e(TAG, "TTS error", e)
            }
        }
    }
    
    private fun safeGoBack() {
        if (isDestroyed.get()) return
        
        mainHandler.post {
            try {
                if (!isDestroyed.get()) {
                    screenManager.pop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error going back", e)
            }
        }
    }
}
