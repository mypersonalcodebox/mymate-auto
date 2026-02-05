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
import com.mymate.auto.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Screen to display a full message in the conversation
 * Allows reading the complete text and TTS playback
 */
class MessageScreen(
    carContext: CarContext,
    private val title: String,
    private val message: String,
    private val onDismiss: () -> Unit
) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private val TAG = "MessageScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val isDestroyed = AtomicBoolean(false)
    
    private val preferencesManager = PreferencesManager(carContext)
    private val ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
    
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
                
                // Auto-speak if TTS enabled
                if (ttsEnabled && ttsReady) {
                    speakMessage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting TTS language", e)
                ttsReady = false
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        // Android Auto has text limits, so we show as much as possible
        // Breaking into multiple rows if needed
        val maxCharsPerRow = 200
        val messageRows = message.chunked(maxCharsPerRow)
        
        val paneBuilder = Pane.Builder()
        
        // Add message content as rows (max 4 rows allowed by Android Auto)
        messageRows.take(4).forEach { chunk ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(chunk)
                    .build()
            )
        }
        
        // If message was truncated, show indicator
        if (messageRows.size > 4) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("... (tekst ingekort)")
                    .build()
            )
        }
        
        // Add TTS button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🔊 Voorlezen")
                .setOnClickListener {
                    speakMessage()
                }
                .build()
        )
        
        // Add back button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("⬅️ Terug")
                .setOnClickListener {
                    safeGoBack()
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun speakMessage() {
        if (isDestroyed.get()) return
        
        if (ttsReady && tts != null) {
            try {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "message")
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
                    onDismiss()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error going back", e)
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
