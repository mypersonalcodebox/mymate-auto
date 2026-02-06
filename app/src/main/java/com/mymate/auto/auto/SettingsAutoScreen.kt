package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.service.TtsManager
import kotlinx.coroutines.runBlocking

/**
 * Settings screen for Android Auto
 */
class SettingsAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    companion object {
        private const val TAG = "SettingsAutoScreen"
    }
    
    private val preferencesManager = PreferencesManager(carContext)
    private val ttsManager = TtsManager.getInstance(carContext)
    private val agentClient = AutoAgentClient.getInstance(carContext)
    
    // TTS settings
    private var ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
        set(value) = runBlocking { preferencesManager.setTtsEnabled(value) }
    
    private var ttsSpeed: Float
        get() = runBlocking { preferencesManager.getTtsSpeedSync() }
        set(value) = runBlocking { preferencesManager.setTtsSpeed(value) }
    
    // Parking settings
    private var autoSaveParking: Boolean
        get() = runBlocking { preferencesManager.getAutoSaveParkingSync() }
        set(value) = runBlocking { preferencesManager.setAutoSaveParking(value) }
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // 1. TTS Toggle
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔊 Tekst-naar-spraak")
                .addText(if (ttsEnabled) "Aan ✅" else "Uit ❌")
                .setOnClickListener {
                    ttsEnabled = !ttsEnabled
                    ttsManager.speak(if (ttsEnabled) "Spraak ingeschakeld" else "Spraak uitgeschakeld")
                    invalidate()
                }
                .build()
        )
        
        // 2. TTS Speed
        val speedLabel = when {
            ttsSpeed <= 0.8f -> "Langzaam 🐢"
            ttsSpeed >= 1.2f -> "Snel 🐇"
            else -> "Normaal"
        }
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🗣️ Spraaksnelheid")
                .addText(speedLabel)
                .setOnClickListener {
                    // Cycle through: normal -> fast -> slow -> normal
                    ttsSpeed = when {
                        ttsSpeed <= 0.8f -> 1.0f  // slow -> normal
                        ttsSpeed >= 1.2f -> 0.75f // fast -> slow
                        else -> 1.25f              // normal -> fast
                    }
                    ttsManager.setSpeed(ttsSpeed)
                    ttsManager.speak("Dit is de nieuwe snelheid")
                    invalidate()
                }
                .build()
        )
        
        // 3. Connection Test
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔗 Verbinding testen")
                .addText(if (agentClient.isConnected()) "Verbonden ✅" else "Niet verbonden ❌")
                .setOnClickListener {
                    testConnection()
                }
                .build()
        )
        
        // 4. Auto-save Parking
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📍 Auto-save parking")
                .addText(if (autoSaveParking) "Aan ✅ (bij Bluetooth disconnect)" else "Uit ❌")
                .setOnClickListener {
                    autoSaveParking = !autoSaveParking
                    ttsManager.speak(if (autoSaveParking) "Auto-save parking aan" else "Auto-save parking uit")
                    invalidate()
                }
                .build()
        )
        
        // 5. Gateway Info
        val gatewayUrl = runBlocking { preferencesManager.getGatewayUrlSync() }
        val maskedUrl = gatewayUrl.replace(Regex("\\d+\\.\\d+\\.\\d+"), "***")
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌐 Gateway")
                .addText(maskedUrl)
                .build()
        )
        
        // 6. App Version
        val versionName = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).versionName
        } catch (e: Exception) { "?" }
        val versionCode = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).longVersionCode
        } catch (e: Exception) { 0L }
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📱 Versie")
                .addText("MyMate v$versionName (build $versionCode)")
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("⚙️ Instellingen")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun testConnection() {
        Log.d(TAG, "Testing connection...")
        ttsManager.speak("Verbinding testen...")
        
        if (agentClient.isConnected()) {
            ttsManager.speak("Verbinding is actief")
        } else {
            // Try to reconnect
            agentClient.connect()
            ttsManager.speak("Opnieuw verbinden...")
        }
        
        invalidate()
    }
}
