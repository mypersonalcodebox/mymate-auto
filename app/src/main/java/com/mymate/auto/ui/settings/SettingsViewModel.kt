package com.mymate.auto.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.remote.OpenClawWebSocket
import com.mymate.auto.util.NetworkUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Socket
import java.net.InetSocketAddress
import java.net.URI

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    private val database = AppDatabase.getInstance(application)
    private val context = application.applicationContext
    
    // Test connection state
    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()
    
    // Live WebSocket connection state (from active connection if any)
    private val _webSocketState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Unknown)
    val webSocketState: StateFlow<WebSocketConnectionState> = _webSocketState.asStateFlow()
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Collect all preferences using combine
        viewModelScope.launch {
            combine(
                preferencesManager.webhookUrl,
                preferencesManager.ttsEnabled,
                preferencesManager.notificationsEnabled,
                preferencesManager.webSocketEnabled,
                preferencesManager.darkMode
            ) { webhookUrl, ttsEnabled, notificationsEnabled, webSocketEnabled, darkMode ->
                PartialState1(webhookUrl, ttsEnabled, notificationsEnabled, webSocketEnabled, darkMode)
            }.combine(
                combine(
                    preferencesManager.autoReconnect,
                    preferencesManager.gatewayUrl,
                    preferencesManager.gatewayToken,
                    preferencesManager.useOpenClawWebSocket,
                    preferencesManager.sessionKey
                ) { autoReconnect, gatewayUrl, gatewayToken, useOpenClawWebSocket, sessionKey ->
                    PartialState2(autoReconnect, gatewayUrl, gatewayToken, useOpenClawWebSocket, sessionKey)
                }
            ) { p1, p2 ->
                Pair(p1, p2)
            }.combine(
                combine(
                    preferencesManager.appLanguage,
                    preferencesManager.autoSaveParking,
                    preferencesManager.wakeWordEnabled
                ) { appLanguage, autoSaveParking, wakeWordEnabled ->
                    PartialState3(appLanguage, autoSaveParking, wakeWordEnabled)
                }
            ) { (p1, p2), p3 ->
                SettingsUiState(
                    webhookUrl = p1.webhookUrl,
                    ttsEnabled = p1.ttsEnabled,
                    notificationsEnabled = p1.notificationsEnabled,
                    webSocketEnabled = p1.webSocketEnabled,
                    darkMode = p1.darkMode,
                    autoReconnect = p2.autoReconnect,
                    gatewayUrl = p2.gatewayUrl,
                    gatewayToken = p2.gatewayToken,
                    useOpenClawWebSocket = p2.useOpenClawWebSocket,
                    sessionKey = p2.sessionKey,
                    appLanguage = p3.appLanguage,
                    autoSaveParking = p3.autoSaveParking,
                    wakeWordEnabled = p3.wakeWordEnabled
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    private data class PartialState1(
        val webhookUrl: String,
        val ttsEnabled: Boolean,
        val notificationsEnabled: Boolean,
        val webSocketEnabled: Boolean,
        val darkMode: Boolean
    )
    
    private data class PartialState2(
        val autoReconnect: Boolean,
        val gatewayUrl: String,
        val gatewayToken: String?,
        val useOpenClawWebSocket: Boolean,
        val sessionKey: String
    )
    
    private data class PartialState3(
        val appLanguage: String,
        val autoSaveParking: Boolean,
        val wakeWordEnabled: Boolean
    )
    
    fun setWebhookUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setWebhookUrl(url)
        }
    }
    
    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setTtsEnabled(enabled)
        }
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setNotificationsEnabled(enabled)
        }
    }
    
    fun setWebSocketEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWebSocketEnabled(enabled)
        }
    }
    
    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(enabled)
        }
    }
    
    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            // This would need to be added to PreferencesManager
            // For now, we'll just update the UI state
        }
    }
    
    // OpenClaw Gateway settings
    fun setGatewayUrl(url: String) {
        viewModelScope.launch {
            preferencesManager.setGatewayUrl(url)
        }
    }
    
    fun setGatewayToken(token: String?) {
        viewModelScope.launch {
            preferencesManager.setGatewayToken(token)
        }
    }
    
    fun setUseOpenClawWebSocket(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUseOpenClawWebSocket(enabled)
        }
    }
    
    fun setSessionKey(key: String) {
        viewModelScope.launch {
            preferencesManager.setSessionKey(key)
        }
    }
    
    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            preferencesManager.setAppLanguage(language)
        }
    }
    
    fun setAutoSaveParking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoSaveParking(enabled)
        }
    }
    
    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWakeWordEnabled(enabled)
        }
    }
    
    fun clearChatHistory() {
        viewModelScope.launch {
            database.chatDao().clearAllMessages()
        }
    }
    
    fun resetPreferences() {
        viewModelScope.launch {
            preferencesManager.setWebhookUrl(PreferencesManager.DEFAULT_WEBHOOK_URL)
            preferencesManager.setTtsEnabled(true)
            preferencesManager.setNotificationsEnabled(true)
            preferencesManager.setWebSocketEnabled(true)
            preferencesManager.setDarkMode(true)
            preferencesManager.setGatewayUrl(PreferencesManager.DEFAULT_GATEWAY_URL)
            preferencesManager.setGatewayToken(null)
            preferencesManager.setUseOpenClawWebSocket(true)
            preferencesManager.setSessionKey(PreferencesManager.DEFAULT_SESSION_KEY)
        }
    }
    
    /**
     * Test the WebSocket connection to the Gateway.
     * Steps:
     * 1. Check if device is online
     * 2. Try TCP socket connection to gateway host:port
     * 3. Report success or failure with details
     */
    fun testConnection() {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing("Controleren of online...")
            
            // Step 1: Check if online
            if (!NetworkUtils.isOnline(context)) {
                _connectionTestState.value = ConnectionTestState.Failed(
                    "Geen internetverbinding",
                    "Controleer of je verbonden bent met WiFi of mobiel netwerk."
                )
                return@launch
            }
            
            _connectionTestState.value = ConnectionTestState.Testing("Verbinden met gateway...")
            
            // Step 2: Parse gateway URL and try TCP connection
            val gatewayUrl = uiState.value.gatewayUrl
            val (host, port) = parseGatewayUrl(gatewayUrl)
            
            if (host.isBlank()) {
                _connectionTestState.value = ConnectionTestState.Failed(
                    "Ongeldige Gateway URL",
                    "De gateway URL '$gatewayUrl' kon niet worden geparsed."
                )
                return@launch
            }
            
            // Try TCP socket connection
            val tcpResult = withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 5000)
                    socket.close()
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure<Unit>(e)
                }
            }
            
            if (tcpResult.isFailure) {
                val error = tcpResult.exceptionOrNull()
                _connectionTestState.value = ConnectionTestState.Failed(
                    "Gateway niet bereikbaar",
                    buildString {
                        appendLine("Kon geen verbinding maken met $host:$port")
                        appendLine()
                        appendLine("Fout: ${error?.localizedMessage ?: "Onbekend"}")
                        appendLine()
                        appendLine("Controleer:")
                        appendLine("• Is de OpenClaw Gateway actief?")
                        appendLine("• Ben je verbonden met Tailscale?")
                        appendLine("• Is het IP-adres/hostname correct?")
                    }
                )
                return@launch
            }
            
            _connectionTestState.value = ConnectionTestState.Testing("TCP OK, testen WebSocket handshake...")
            
            // Step 3: Try WebSocket handshake
            val wsResult = withContext(Dispatchers.IO) {
                testWebSocketHandshake(gatewayUrl, uiState.value.gatewayToken)
            }
            
            if (wsResult.isSuccess) {
                _connectionTestState.value = ConnectionTestState.Success(
                    "Verbinding succesvol!",
                    buildString {
                        appendLine("✅ Online: Ja")
                        appendLine("✅ TCP verbinding: $host:$port")
                        appendLine("✅ WebSocket handshake: OK")
                        appendLine()
                        appendLine("Gateway is bereikbaar en werkt correct.")
                    }
                )
            } else {
                val error = wsResult.exceptionOrNull()
                _connectionTestState.value = ConnectionTestState.Failed(
                    "WebSocket handshake mislukt",
                    buildString {
                        appendLine("TCP verbinding werkt, maar WebSocket niet.")
                        appendLine()
                        appendLine("Fout: ${error?.localizedMessage ?: "Onbekend"}")
                        appendLine()
                        appendLine("Controleer:")
                        appendLine("• Is de auth token correct?")
                        appendLine("• Draait de Gateway service?")
                    }
                )
            }
        }
    }
    
    fun resetConnectionTest() {
        _connectionTestState.value = ConnectionTestState.Idle
    }
    
    private fun parseGatewayUrl(url: String): Pair<String, Int> {
        return try {
            // Convert ws:// to http:// for URI parsing
            val normalizedUrl = url
                .replace("ws://", "http://")
                .replace("wss://", "https://")
            val uri = URI(normalizedUrl)
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 18789
            host to port
        } catch (e: Exception) {
            "" to 18789
        }
    }
    
    private suspend fun testWebSocketHandshake(gatewayUrl: String, token: String?): Result<Unit> {
        return try {
            val testSocket = OpenClawWebSocket(gatewayUrl, token)
            testSocket.connect(autoReconnect = false)
            
            // Wait for connection with timeout
            withTimeout(10_000) {
                testSocket.connectionState.first { state ->
                    state == OpenClawWebSocket.ConnectionState.CONNECTED ||
                    state == OpenClawWebSocket.ConnectionState.ERROR
                }
            }
            
            val finalState = testSocket.connectionState.value
            testSocket.disconnect()
            testSocket.cleanup()
            
            if (finalState == OpenClawWebSocket.ConnectionState.CONNECTED) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("WebSocket connection failed (state: $finalState)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update the live WebSocket state (called from ChatRepository or service)
     */
    fun updateWebSocketState(state: WebSocketConnectionState) {
        _webSocketState.value = state
    }
}

data class SettingsUiState(
    val webhookUrl: String = PreferencesManager.DEFAULT_WEBHOOK_URL,
    val ttsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val webSocketEnabled: Boolean = true,
    val darkMode: Boolean = true,
    val autoReconnect: Boolean = true,
    // OpenClaw Gateway settings
    val gatewayUrl: String = PreferencesManager.DEFAULT_GATEWAY_URL,
    val gatewayToken: String? = null,
    val useOpenClawWebSocket: Boolean = true,
    val sessionKey: String = PreferencesManager.DEFAULT_SESSION_KEY,
    // Language setting
    val appLanguage: String = PreferencesManager.DEFAULT_LANGUAGE,
    // Bluetooth parking auto-save
    val autoSaveParking: Boolean = false,
    // Wake word detection
    val wakeWordEnabled: Boolean = false
)

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    data class Testing(val message: String) : ConnectionTestState()
    data class Success(val title: String, val details: String) : ConnectionTestState()
    data class Failed(val title: String, val details: String) : ConnectionTestState()
}

enum class WebSocketConnectionState {
    Unknown,
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Error
}
