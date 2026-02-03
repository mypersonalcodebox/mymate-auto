package com.mymate.auto.auto

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import java.util.*

class VoiceInputScreen(
    carContext: CarContext,
    private val onMessageReceived: (String) -> Unit
) : Screen(carContext) {
    
    private var recognizedText: String? = null
    private var isListening = false
    
    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()
        
        if (recognizedText != null) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Herkende tekst:")
                    .addText(recognizedText ?: "")
                    .build()
            )
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("✓ Verstuur")
                    .setOnClickListener {
                        recognizedText?.let { text ->
                            onMessageReceived(text)
                            screenManager.pop()
                        }
                    }
                    .build()
            )
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("🎤 Opnieuw")
                    .setOnClickListener {
                        startVoiceRecognition()
                    }
                    .build()
            )
        } else {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Spraakherkenning")
                    .addText("Tik op de microfoon om te spreken")
                    .build()
            )
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("🎤 Start spraakherkenning")
                    .setOnClickListener {
                        startVoiceRecognition()
                    }
                    .build()
            )
        }
        
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("⬅️ Annuleren")
                .setOnClickListener {
                    screenManager.pop()
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Spraak invoer")
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Wat wil je vragen aan MyMate?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            carContext.startActivity(intent)
            // Note: In a real implementation, you'd need to handle the result
            // through an ActivityResultContract or similar mechanism
            // For Android Auto, voice input is often handled through the car's built-in system
        } catch (e: Exception) {
            recognizedText = "Spraakherkenning niet beschikbaar"
            invalidate()
        }
    }
}
