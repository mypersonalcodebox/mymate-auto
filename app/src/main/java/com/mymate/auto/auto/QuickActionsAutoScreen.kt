package com.mymate.auto.auto

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.mymate.auto.service.TtsManager
import kotlinx.coroutines.*

/**
 * Quick Actions submenu - self-contained with its own message handling
 * 
 * Each action opens VoiceInputScreen, then sends the combined message directly.
 * No callbacks to parent screen needed.
 */
class QuickActionsAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    companion object {
        private const val TAG = "QuickActionsAutoScreen"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val agentClient = AutoAgentClient.getInstance(carContext)
    private val ttsManager = TtsManager.getInstance(carContext)
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Quick direct action - no voice input needed
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📅 Agenda")
                .addText("Bekijk je afspraken van vandaag")
                .setOnClickListener { 
                    sendDirectMessage("Wat staat er vandaag op mijn agenda?")
                }
                .build()
        )
        
        // Template actions - user speaks to complete
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📝 Discussie starten")
                .addText("\"Laten we discussiëren over...\"")
                .setOnClickListener { 
                    openTemplatedVoice("Laten we discussiëren over", "discussion")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("💡 Brainstormen")
                .addText("\"Help me brainstormen over...\"")
                .setOnClickListener { 
                    openTemplatedVoice("Help me brainstormen over", "brainstorm")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔍 Research")
                .addText("\"Zoek informatie over...\"")
                .setOnClickListener { 
                    openTemplatedVoice("Zoek informatie over", "research")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("💻 Code hulp")
                .addText("\"Help me met code voor...\"")
                .setOnClickListener { 
                    openTemplatedVoice("Help me met code voor", "code")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎯 Plan maken")
                .addText("\"Maak een plan voor...\"")
                .setOnClickListener { 
                    openTemplatedVoice("Maak een plan voor", "plan")
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("⚡ Snelle acties")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    /**
     * Open voice input with a template prefix
     * User speaks to complete, then full message is sent
     * 
     * @param prefix The text prefix to prepend to user input
     * @param context The VoiceInputScreen context for appropriate hints
     */
    private fun openTemplatedVoice(prefix: String, context: String) {
        screenManager.push(
            VoiceInputScreen(carContext, context) { userInput ->
                val fullMessage = "$prefix $userInput"
                sendMessageAndShowResponse(fullMessage)
            }
        )
    }
    
    /**
     * Send a direct message (no voice input needed)
     */
    private fun sendDirectMessage(message: String) {
        sendMessageAndShowResponse(message)
    }
    
    /**
     * Send message via WebSocket and show response with TTS
     */
    private fun sendMessageAndShowResponse(message: String) {
        Log.d(TAG, "Sending: ${message.take(50)}...")
        
        // Show a processing message
        screenManager.push(
            MessageScreen(
                carContext,
                "⏳ Even geduld...",
                "Vraag wordt verwerkt"
            ) { screenManager.pop() }
        )
        
        scope.launch {
            try {
                val result = agentClient.sendMessage(message)
                
                result.onSuccess { response ->
                    Log.d(TAG, "Got response: ${response.take(50)}...")
                    
                    mainHandler.post {
                        // Pop the "processing" message
                        screenManager.pop()
                        
                        // Show response in MessageScreen
                        screenManager.push(
                            MessageScreen(
                                carContext,
                                "✅ MyMate",
                                response.take(200) + if (response.length > 200) "..." else ""
                            ) { screenManager.pop() }
                        )
                        
                        // Speak response
                        ttsManager.speak(response)
                    }
                }
                
                result.onFailure { error ->
                    Log.e(TAG, "Request failed: ${error.message}")
                    
                    mainHandler.post {
                        screenManager.pop()
                        screenManager.push(
                            MessageScreen(
                                carContext,
                                "❌ Fout",
                                error.message ?: "Er ging iets mis"
                            ) { screenManager.pop() }
                        )
                        ttsManager.speak("Sorry, er ging iets mis")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
                
                mainHandler.post {
                    screenManager.pop()
                    screenManager.push(
                        MessageScreen(
                            carContext,
                            "❌ Fout",
                            e.message ?: "Onbekende fout"
                        ) { screenManager.pop() }
                    )
                }
            }
        }
    }
}
