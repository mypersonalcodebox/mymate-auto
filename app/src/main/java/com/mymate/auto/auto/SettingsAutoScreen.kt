package com.mymate.auto.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.mymate.auto.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking

/**
 * Simple settings screen for Android Auto
 */
class SettingsAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val preferencesManager = PreferencesManager(carContext)
    
    private var ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
        set(value) = runBlocking { preferencesManager.setTtsEnabled(value) }
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // 1. TTS Toggle
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔊 Tekst-naar-spraak")
                .addText(if (ttsEnabled) "Aan ✅" else "Uit ❌")
                .setOnClickListener {
                    ttsEnabled = !ttsEnabled
                    invalidate()
                }
                .build()
        )
        
        // 2. Gateway Info
        val gatewayUrl = runBlocking { preferencesManager.getGatewayUrlSync() }
        val maskedUrl = gatewayUrl.replace(Regex("\\d+\\.\\d+\\.\\d+"), "***")
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌐 Gateway")
                .addText(maskedUrl)
                .build()
        )
        
        // 3. App Version
        val versionName = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).versionName
        } catch (e: Exception) { "?" }
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📱 Versie")
                .addText("MyMate v$versionName")
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("⚙️ Instellingen")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
