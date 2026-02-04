package com.mymate.auto.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    private val database = AppDatabase.getInstance(application)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Collect all preferences
        viewModelScope.launch {
            combine(
                preferencesManager.webhookUrl,
                preferencesManager.ttsEnabled,
                preferencesManager.notificationsEnabled,
                preferencesManager.webSocketEnabled,
                preferencesManager.darkMode,
                preferencesManager.autoReconnect,
                preferencesManager.gatewayUrl,
                preferencesManager.gatewayToken,
                preferencesManager.useOpenClawWebSocket,
                preferencesManager.sessionKey
            ) { values ->
                SettingsUiState(
                    webhookUrl = values[0] as String,
                    ttsEnabled = values[1] as Boolean,
                    notificationsEnabled = values[2] as Boolean,
                    webSocketEnabled = values[3] as Boolean,
                    darkMode = values[4] as Boolean,
                    autoReconnect = values[5] as Boolean,
                    gatewayUrl = values[6] as String,
                    gatewayToken = values[7] as? String,
                    useOpenClawWebSocket = values[8] as Boolean,
                    sessionKey = values[9] as String
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
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
    val sessionKey: String = PreferencesManager.DEFAULT_SESSION_KEY
)
