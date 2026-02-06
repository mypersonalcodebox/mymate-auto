package com.mymate.auto.auto

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.service.TtsManager
import kotlinx.coroutines.*

/**
 * Simple voice-first assistant screen for Android Auto
 * 
 * Minimal UI for driving - just a button and last response.
 * Flow: Tap button → Speak → Get response via WebSocket → TTS speaks it
 */
class VoiceAssistantScreen(carContext: CarContext) : Screen(carContext) {
    
    companion object {
        private const val TAG = "VoiceAssistantScreen"
        private const val MAX_RESPONSE_LENGTH = 100
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val agentClient = AutoAgentClient.getInstance(carContext)
    private val ttsManager = TtsManager.getInstance(carContext)
    
    // UI State
    private var lastResponse: String = "Tik op de knop om te beginnen"
    private var isProcessing: Boolean = false
    private var connectionStatus: String = ""
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cleanup()
            }
        })
        
        // Monitor connection state
        scope.launch {
            agentClient.connectionState.collect { state ->
                connectionStatus = agentClient.getStatusText()
                mainHandler.post { invalidate() }
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()
        
        // Show current status/last response
        val displayText = when {
            isProcessing -> "⏳ Even geduld..."
            else -> lastResponse.take(MAX_RESPONSE_LENGTH) + 
                    if (lastResponse.length > MAX_RESPONSE_LENGTH) "..." else ""
        }
        
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(displayText)
                .addText(if (!agentClient.isConnected()) "⚠️ $connectionStatus" else "")
                .build()
        )
        
        // Main voice button - only enabled when not processing
        if (!isProcessing) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("🎤 Spreek nu")
                    .setOnClickListener { startVoiceInput() }
                    .build()
            )
        } else {
            // Show processing indicator
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("⏳ Bezig...")
                    .setOnClickListener { /* disabled */ }
                    .build()
            )
        }
        
        // Repeat last response with TTS
        if (lastResponse.isNotEmpty() && lastResponse != "Tik op de knop om te beginnen" && !isProcessing) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("🔊 Herhaal")
                    .setOnClickListener { repeatLastResponse() }
                    .build()
            )
        }
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("🎤 MyMate")
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun startVoiceInput() {
        Log.d(TAG, "Starting voice input")
        
        screenManager.push(
            VoiceInputScreen(carContext, "conversation") { userMessage ->
                handleUserMessage(userMessage)
            }
        )
    }
    
    private fun handleUserMessage(message: String) {
        Log.d(TAG, "User said: ${message.take(50)}...")
        
        isProcessing = true
        lastResponse = "🎤 \"${message.take(50)}${if (message.length > 50) "..." else ""}\""
        invalidate()
        
        scope.launch {
            try {
                val result = agentClient.sendMessage(message)
                
                result.onSuccess { response ->
                    Log.d(TAG, "Got response: ${response.take(50)}...")
                    
                    mainHandler.post {
                        lastResponse = response
                        isProcessing = false
                        invalidate()
                        
                        // Speak the response
                        ttsManager.speak(response)
                    }
                }
                
                result.onFailure { error ->
                    Log.e(TAG, "Request failed: ${error.message}")
                    
                    mainHandler.post {
                        lastResponse = "❌ ${error.message ?: "Er ging iets mis"}"
                        isProcessing = false
                        invalidate()
                        
                        ttsManager.speak("Sorry, er ging iets mis")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
                
                mainHandler.post {
                    lastResponse = "❌ ${e.message ?: "Onbekende fout"}"
                    isProcessing = false
                    invalidate()
                }
            }
        }
    }
    
    private fun repeatLastResponse() {
        if (lastResponse.isNotEmpty() && !lastResponse.startsWith("❌")) {
            ttsManager.speak(lastResponse)
        }
    }
    
    private fun cleanup() {
        try {
            scope.cancel()
            ttsManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }
}
