package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

class SettingsAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "SettingsAutoScreen"
    private val prefs = carContext.getSharedPreferences("mymate_prefs", CarContext.MODE_PRIVATE)
    
    private var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()
    
    private var gatewayHost: String
        get() = prefs.getString("gateway_host", "100.124.24.27") ?: "100.124.24.27"
        set(value) = prefs.edit().putString("gateway_host", value).apply()
    
    private var gatewayPort: Int
        get() = prefs.getInt("gateway_port", 18789)
        set(value) = prefs.edit().putInt("gateway_port", value).apply()
    
    private var webhookUrl: String
        get() = prefs.getString("webhook_url", "http://100.124.24.27:18791/auto") 
            ?: "http://100.124.24.27:18791/auto"
        set(value) = prefs.edit().putString("webhook_url", value).apply()
    
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
        
        // Gateway Host
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌐 Gateway Host")
                .addText(gatewayHost)
                .setOnClickListener {
                    // Show info - can't edit via voice in Android Auto easily
                    screenManager.push(
                        MessageScreen(carContext, 
                            "Gateway Host",
                            "Huidige host: $gatewayHost\n\nWijzig deze instelling in de phone app."
                        ) {
                            screenManager.pop()
                        }
                    )
                }
                .build()
        )
        
        // Gateway Port
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔌 Gateway Port")
                .addText(gatewayPort.toString())
                .setOnClickListener {
                    screenManager.push(
                        MessageScreen(carContext, 
                            "Gateway Port",
                            "Huidige port: $gatewayPort\n\nWijzig deze instelling in de phone app."
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
        
        // Clear action history
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🗑️ Actie-geschiedenis wissen")
                .addText("Reset quick action volgorde")
                .setOnClickListener {
                    prefs.edit()
                        .remove("action_usage")
                        .remove("action_last_used")
                        .apply()
                    screenManager.push(
                        MessageScreen(carContext, 
                            "✅ Gewist",
                            "Actie-geschiedenis is gewist. Quick actions staan weer in standaard volgorde."
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
                    screenManager.push(ConnectionTestScreen(carContext, gatewayHost, gatewayPort))
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
    private val host: String,
    private val port: Int
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
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 5000)
                socket.close()
                testStatus = "✅ Verbinding succesvol!\n\nGateway is bereikbaar op $host:$port"
                testComplete = true
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                testStatus = "❌ Verbinding mislukt\n\n${e.localizedMessage}\n\nControleer:\n• Is de gateway actief?\n• Ben je verbonden met Tailscale?\n• Is het IP-adres correct?"
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
