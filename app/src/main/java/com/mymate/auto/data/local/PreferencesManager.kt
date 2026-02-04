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
        
        const val DEFAULT_WEBHOOK_URL = "http://100.124.24.27:18791/auto"
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
}
