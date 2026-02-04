package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.mymate.auto.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking

class SettingsAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "SettingsAutoScreen"
    private val preferencesManager = PreferencesManager(carContext)
    
    private var ttsEnabled: Boolean
        get() = runBlocking { preferencesManager.getTtsEnabledSync() }
        set(value) = runBlocking { preferencesManager.setTtsEnabled(value) }
    
    private val gatewayUrl: String
        get() = runBlocking { preferencesManager.getGatewayUrlSync() }
    
    private val gatewayToken: String
        get() = runBlocking { preferencesManager.getGatewayTokenSync() }
    
    private val webhookUrl: String
        get() = runBlocking { preferencesManager.getWebhookUrlSync() }
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // TTS Toggle
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔊 Tekst-naar-spraak")
                .addText(if (ttsEnabled) "Ingeschakeld ✅" else "Uitgeschakeld ❌")
                .setOnClickListener {
                    ttsEnabled = !ttsEnabled
                    invalidate()
                }
                .build()
        )
        
        // Gateway URL
        val gatewayDisplay = if (gatewayUrl.length > 40) gatewayUrl.take(40) + "..." else gatewayUrl
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌐 Gateway URL")
                .addText(gatewayDisplay)
                .setOnClickListener {
                    screenManager.push(
                        MessageScreen(carContext, 
                            "Gateway URL",
                            "Huidige URL: $gatewayUrl\n\nWijzig deze instelling in de phone app."
                        ) {
                            screenManager.pop()
                        }
                    )
                }
                .build()
        )
        
        // Gateway Token
        val tokenDisplay = if (gatewayToken.isNotEmpty()) "••••••••" else "(niet ingesteld)"
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔑 Gateway Token")
                .addText(tokenDisplay)
                .setOnClickListener {
                    screenManager.push(
                        MessageScreen(carContext, 
                            "Gateway Token",
                            if (gatewayToken.isNotEmpty()) "Token is ingesteld.\n\nWijzig in de phone app." else "Geen token ingesteld.\n\nStel in via de phone app."
                        ) {
                            screenManager.pop()
                        }
                    )
                }
                .build()
        )
        
        // Webhook URL
        val webhookDisplay = if (webhookUrl.length > 40) webhookUrl.take(40) + "..." else webhookUrl
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔗 Webhook URL")
                .addText(webhookDisplay)
                .setOnClickListener {
                    screenManager.push(
                        MessageScreen(carContext, 
                            "Webhook URL",
                            "Huidige URL:\n$webhookUrl\n\nWijzig deze instelling in de phone app."
                        ) {
                            screenManager.pop()
                        }
                    )
                }
                .build()
        )
        
        // Clear action history - Note: This would require a clearActionUsage method in PreferencesManager
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🗑️ Actie-geschiedenis wissen")
                .addText("Reset quick action volgorde")
                .setOnClickListener {
                    screenManager.push(
                        MessageScreen(carContext, 
                            "ℹ️ Info",
                            "Actie-geschiedenis wissen is beschikbaar in de phone app."
                        ) {
                            screenManager.pop()
                        }
                    )
                }
                .build()
        )
        
        // App version
        val version = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).versionName ?: "Onbekend"
        } catch (e: Exception) {
            "Onbekend"
        }
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📱 App versie")
                .addText(version)
                .build()
        )
        
        // Connection test
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🧪 Test verbinding")
                .addText("Controleer of gateway bereikbaar is")
                .setOnClickListener {
                    screenManager.push(ConnectionTestScreen(carContext, gatewayUrl))
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("⚙️ Instellingen")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class ConnectionTestScreen(
    carContext: CarContext,
    private val gatewayUrl: String
) : Screen(carContext) {
    
    private val TAG = "ConnectionTestScreen"
    
    @Volatile
    private var testStatus = "Testen..."
    
    @Volatile
    private var testComplete = false
    
    init {
        testConnection()
    }
    
    private fun testConnection() {
        Thread {
            try {
                // Parse host and port from URL (e.g., "ws://host:port" or "http://host:port")
                val url = java.net.URI(gatewayUrl)
                val host = url.host ?: throw IllegalArgumentException("Geen host in URL")
                val port = if (url.port > 0) url.port else 18789
                
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 5000)
                socket.close()
                testStatus = "✅ Verbinding succesvol!\n\nGateway is bereikbaar op $host:$port"
                testComplete = true
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                testStatus = "❌ Verbinding mislukt\n\n${e.localizedMessage}\n\nControleer:\n• Is de gateway actief?\n• Ben je verbonden met Tailscale?\n• Is de URL correct?"
                testComplete = true
            }
            
            try {
                invalidate()
            } catch (e: Exception) {
                Log.e(TAG, "Invalidate failed", e)
            }
        }.start()
    }
    
    override fun onGetTemplate(): Template {
        val builder = MessageTemplate.Builder(testStatus)
            .setTitle("🧪 Verbindingstest")
            .setHeaderAction(Action.BACK)
        
        if (testComplete) {
            builder.addAction(
                Action.Builder()
                    .setTitle("OK")
                    .setOnClickListener {
                        screenManager.pop()
                    }
                    .build()
            )
            
            builder.addAction(
                Action.Builder()
                    .setTitle("🔄 Opnieuw")
                    .setOnClickListener {
                        testStatus = "Testen..."
                        testComplete = false
                        invalidate()
                        testConnection()
                    }
                    .build()
            )
        }
        
        return builder.build()
    }
}
