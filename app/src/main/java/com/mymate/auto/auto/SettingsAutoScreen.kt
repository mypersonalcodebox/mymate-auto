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
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Android Auto max 6 items!
        
        // 1. TTS Toggle (essential for driving)
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
        
        // 2. Connection Test (troubleshooting)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🧪 Test verbinding")
                .addText("Controleer gateway")
                .setBrowsable(true)
                .setOnClickListener {
                    val gatewayUrl = runBlocking { preferencesManager.getGatewayUrlSync() }
                    screenManager.push(ConnectionTestScreen(carContext, gatewayUrl))
                }
                .build()
        )
        
        // 3. Agenda & Briefing (extra feature)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📅 Agenda & Briefing")
                .addText("Afspraken en dagelijks overzicht")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(AgendaBriefingMenuScreen(carContext))
                }
                .build()
        )
        
        // 4. Quick Actions (frequently used commands)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⚡ Quick Actions")
                .addText("Snelle commando's")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(QuickActionsScreen(carContext))
                }
                .build()
        )
        
        // 5. Developer Tools
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🛠️ Developer")
                .addText("Taken en projecten")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(DeveloperActionsScreen(carContext))
                }
                .build()
        )
        
        // 6. App Info (version, gateway config)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📱 Over MyMate")
                .addText("Versie en configuratie")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(AppInfoScreen(carContext))
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

/**
 * Submenu for Agenda and Morning Briefing
 */
class AgendaBriefingMenuScreen(carContext: CarContext) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📅 Agenda")
                .addText("Vandaag en morgen")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(AgendaAutoScreen(carContext))
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌅 Ochtend Briefing")
                .addText("Weer, agenda, overzicht")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(MorningBriefingScreen(carContext))
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("📅 Agenda & Briefing")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

/**
 * App info screen with version and gateway configuration
 */
class AppInfoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val preferencesManager = PreferencesManager(carContext)
    
    override fun onGetTemplate(): Template {
        val version = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        
        val gatewayUrl = runBlocking { preferencesManager.getGatewayUrlSync() }
        val webhookUrl = runBlocking { preferencesManager.getWebhookUrlSync() }
        val hasToken = runBlocking { preferencesManager.getGatewayTokenSync().isNotEmpty() }
        
        val listBuilder = ItemList.Builder()
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📱 Versie")
                .addText(version)
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌐 Gateway")
                .addText(gatewayUrl.take(45))
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔑 Token")
                .addText(if (hasToken) "Ingesteld ✅" else "Niet ingesteld ❌")
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("ℹ️ Configuratie wijzigen")
                .addText("Gebruik de telefoon app")
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("📱 Over MyMate")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

/**
 * Connection test screen
 */
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
                val url = java.net.URI(gatewayUrl)
                val host = url.host ?: throw IllegalArgumentException("Geen host in URL")
                val port = if (url.port > 0) url.port else 18789
                
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 5000)
                socket.close()
                testStatus = "✅ Verbinding OK!\n\n$host:$port bereikbaar"
                testComplete = true
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                testStatus = "❌ Mislukt\n\n${e.localizedMessage}"
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
                    .setOnClickListener { screenManager.pop() }
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
