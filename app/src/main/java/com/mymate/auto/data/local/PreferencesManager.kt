package com.mymate.auto.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mymate_settings")

class PreferencesManager(private val context: Context) {
    
    private val gson = Gson()
    
    companion object {
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val WEBSOCKET_ENABLED = booleanPreferencesKey("websocket_enabled")
        val ACTION_USAGE = stringPreferencesKey("action_usage")
        val ACTION_LAST_USED = stringPreferencesKey("action_last_used")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        
        // OpenClaw Gateway settings
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        val USE_OPENCLAW_WEBSOCKET = booleanPreferencesKey("use_openclaw_websocket")
        val SESSION_KEY = stringPreferencesKey("session_key")
        
        // Onboarding
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        
        // Parking settings
        val AUTO_SAVE_PARKING = booleanPreferencesKey("auto_save_parking")
        val SEND_PARKING_TO_TELEGRAM = booleanPreferencesKey("send_parking_to_telegram")
        val PARKING_RETENTION_DAYS = intPreferencesKey("parking_retention_days")
        
        const val DEFAULT_WEBHOOK_URL = ""
        const val DEFAULT_GATEWAY_URL = "ws://your-gateway:18789"
        const val DEFAULT_GATEWAY_TOKEN = ""
        const val DEFAULT_SESSION_KEY = "agent:main:mymate"
    }
    
    val webhookUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[WEBHOOK_URL] ?: DEFAULT_WEBHOOK_URL
    }
    
    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TTS_ENABLED] ?: true
    }
    
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }
    
    val webSocketEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        // WebSocket is DISABLED by default - the webhook doesn't have a WS endpoint
        prefs[WEBSOCKET_ENABLED] ?: false
    }
    
    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true
    }
    
    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_RECONNECT] ?: true
    }
    
    // OpenClaw Gateway settings
    val gatewayUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GATEWAY_URL] ?: DEFAULT_GATEWAY_URL
    }
    
    val gatewayToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GATEWAY_TOKEN] ?: DEFAULT_GATEWAY_TOKEN
    }
    
    val useOpenClawWebSocket: Flow<Boolean> = context.dataStore.data.map { prefs ->
        // OpenClaw WebSocket is ENABLED by default for real-time communication
        prefs[USE_OPENCLAW_WEBSOCKET] ?: true
    }
    
    val sessionKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SESSION_KEY] ?: DEFAULT_SESSION_KEY
    }
    
    suspend fun setWebhookUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[WEBHOOK_URL] = url
        }
    }
    
    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TTS_ENABLED] = enabled
        }
    }
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED] = enabled
        }
    }
    
    suspend fun setWebSocketEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[WEBSOCKET_ENABLED] = enabled
        }
    }
    
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = enabled
        }
    }
    
    // OpenClaw Gateway settings
    suspend fun setGatewayUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[GATEWAY_URL] = url
        }
    }
    
    suspend fun setGatewayToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token != null) {
                prefs[GATEWAY_TOKEN] = token
            } else {
                prefs.remove(GATEWAY_TOKEN)
            }
        }
    }
    
    suspend fun setUseOpenClawWebSocket(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[USE_OPENCLAW_WEBSOCKET] = enabled
        }
    }
    
    suspend fun setSessionKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[SESSION_KEY] = key
        }
    }
    
    suspend fun getGatewayUrlSync(): String {
        return context.dataStore.data.first()[GATEWAY_URL] ?: DEFAULT_GATEWAY_URL
    }
    
    suspend fun getGatewayTokenSync(): String {
        return context.dataStore.data.first()[GATEWAY_TOKEN] ?: DEFAULT_GATEWAY_TOKEN
    }
    
    suspend fun getSessionKeySync(): String {
        return context.dataStore.data.first()[SESSION_KEY] ?: DEFAULT_SESSION_KEY
    }
    
    suspend fun getUseOpenClawWebSocketSync(): Boolean {
        return context.dataStore.data.first()[USE_OPENCLAW_WEBSOCKET] ?: true
    }
    
    // Onboarding
    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_CONFIGURED] ?: false
    }
    
    suspend fun setIsConfigured(configured: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_CONFIGURED] = configured
        }
    }
    
    suspend fun getIsConfiguredSync(): Boolean {
        return context.dataStore.data.first()[IS_CONFIGURED] ?: false
    }
    
    // Action usage tracking
    suspend fun incrementActionUsage(actionId: String) {
        context.dataStore.edit { prefs ->
            val usageJson = prefs[ACTION_USAGE] ?: "{}"
            val intMapType = object : TypeToken<MutableMap<String, Int>>() {}.type
            val usageMap: MutableMap<String, Int> = gson.fromJson(usageJson, intMapType) ?: mutableMapOf()
            usageMap[actionId] = (usageMap[actionId] ?: 0) + 1
            prefs[ACTION_USAGE] = gson.toJson(usageMap)
            
            // Update last used - use Long type for timestamps!
            val lastUsedJson = prefs[ACTION_LAST_USED] ?: "{}"
            val longMapType = object : TypeToken<MutableMap<String, Long>>() {}.type
            val lastUsedMap: MutableMap<String, Long> = gson.fromJson(lastUsedJson, longMapType) ?: mutableMapOf()
            lastUsedMap[actionId] = System.currentTimeMillis()
            prefs[ACTION_LAST_USED] = gson.toJson(lastUsedMap)
        }
    }
    
    suspend fun getActionUsage(): Map<String, Int> {
        val prefs = context.dataStore.data.first()
        val usageJson = prefs[ACTION_USAGE] ?: "{}"
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(usageJson, type) ?: emptyMap()
    }
    
    suspend fun getActionLastUsed(): Map<String, Long> {
        val prefs = context.dataStore.data.first()
        val lastUsedJson = prefs[ACTION_LAST_USED] ?: "{}"
        val type = object : TypeToken<Map<String, Long>>() {}.type
        return gson.fromJson(lastUsedJson, type) ?: emptyMap()
    }
    
    suspend fun getWebhookUrlSync(): String {
        return context.dataStore.data.first()[WEBHOOK_URL] ?: DEFAULT_WEBHOOK_URL
    }
    
    suspend fun getTtsEnabledSync(): Boolean {
        return context.dataStore.data.first()[TTS_ENABLED] ?: true
    }
    
    // Parking settings
    val autoSaveParking: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_SAVE_PARKING] ?: true
    }
    
    val sendParkingToTelegram: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SEND_PARKING_TO_TELEGRAM] ?: true
    }
    
    val parkingRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PARKING_RETENTION_DAYS] ?: 30
    }
    
    suspend fun setAutoSaveParking(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_SAVE_PARKING] = enabled
        }
    }
    
    suspend fun setSendParkingToTelegram(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SEND_PARKING_TO_TELEGRAM] = enabled
        }
    }
    
    suspend fun setParkingRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[PARKING_RETENTION_DAYS] = days
        }
    }
    
    suspend fun getAutoSaveParkingSync(): Boolean {
        return context.dataStore.data.first()[AUTO_SAVE_PARKING] ?: true
    }
    
    suspend fun getSendParkingToTelegramSync(): Boolean {
        return context.dataStore.data.first()[SEND_PARKING_TO_TELEGRAM] ?: true
    }
}
