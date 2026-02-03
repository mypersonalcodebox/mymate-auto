package com.mymate.auto.auto

import android.speech.tts.TextToSpeech
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.*

class ResponseScreen(
    carContext: CarContext,
    private val response: String
) : Screen(carContext), TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    private val ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
    
    init {
        tts = TextToSpeech(carContext, this)
        
        // Clean up TTS when screen is destroyed
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
        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Antwoord")
                    .addText(response)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🔊 Voorlezen")
                    .setOnClickListener {
                        if (ttsReady) {
                            tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "response")
                        }
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⬅️ Terug")
                    .setOnClickListener {
                        screenManager.pop()
                    }
                    .build()
            )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("MyMate")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
