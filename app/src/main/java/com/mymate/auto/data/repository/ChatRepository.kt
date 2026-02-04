package com.mymate.auto.data.repository

import android.content.Context
import android.util.Log
import com.mymate.auto.data.local.ChatDao
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ChatMessage
import com.mymate.auto.data.model.QuickAction
import com.mymate.auto.data.model.QuickActions
import com.mymate.auto.data.remote.MyMateApiClient
import com.mymate.auto.data.remote.OpenClawWebSocket
import com.mymate.auto.service.TtsManager
import com.mymate.auto.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiClient: MyMateApiClient,
    private val preferencesManager: PreferencesManager,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "ChatRepository"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // OpenClaw WebSocket client
    private var openClawWebSocket: OpenClawWebSocket? = null
    
    private val _webSocketState = MutableStateFlow(OpenClawWebSocket.ConnectionState.DISCONNECTED)
    val webSocketState: StateFlow<OpenClawWebSocket.ConnectionState> = _webSocketState
    
    init {
        // Initialize WebSocket connection if enabled
        scope.launch {
            if (preferencesManager.getUseOpenClawWebSocketSync()) {
                initializeOpenClawWebSocket()
            }
        }
    }
    
    private suspend fun initializeOpenClawWebSocket() {
        val gatewayUrl = preferencesManager.getGatewayUrlSync()
        val token = preferencesManager.getGatewayTokenSync()
        val sessionKey = preferencesManager.getSessionKeySync()
        
        Log.d(TAG, "Initializing OpenClaw WebSocket: $gatewayUrl")
        
        openClawWebSocket?.cleanup()
        openClawWebSocket = OpenClawWebSocket(
            gatewayUrl = gatewayUrl,
            authToken = token
        ).apply {
            setSessionKey(sessionKey)
            
            // Collect connection state
            scope.launch {
                connectionState.collect { state ->
                    Log.d(TAG, "WebSocket state: $state")
                    _webSocketState.value = state
                }
            }
            
            // Note: Chat responses are saved in sendViaWebSocket, not here
            // to avoid duplicate messages
            
            connect(autoReconnect = true)
        }
    }
    
    fun connectWebSocket() {
        scope.launch {
            if (openClawWebSocket == null) {
                initializeOpenClawWebSocket()
            } else {
                openClawWebSocket?.connect(autoReconnect = true)
            }
        }
    }
    
    fun disconnectWebSocket() {
        openClawWebSocket?.disconnect()
    }
    
    fun getRecentMessages(limit: Int = 100): Flow<List<ChatMessage>> {
        return chatDao.getRecentMessages(limit)
    }
    
    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatDao.getAllMessages()
    }
    
    suspend fun sendMessage(
        message: String,
        quickActionId: String? = null
    ): Result<ChatMessage> {
        // Save user message
        val userMessage = ChatMessage(
            content = message,
            isFromUser = true,
            quickActionId = quickActionId
        )
        chatDao.insertMessage(userMessage)
        
        // Track action usage if applicable
        quickActionId?.let {
            preferencesManager.incrementActionUsage(it)
        }
        
        // Try WebSocket first if enabled and connected
        val useWebSocket = preferencesManager.getUseOpenClawWebSocketSync()
        if (useWebSocket && _webSocketState.value == OpenClawWebSocket.ConnectionState.CONNECTED) {
            Log.d(TAG, "Sending via OpenClaw WebSocket")
            return sendViaWebSocket(message, quickActionId)
        }
        
        // Fallback to HTTP webhook
        Log.d(TAG, "Sending via HTTP webhook")
        return sendViaWebhook(message, quickActionId)
    }
    
    private suspend fun sendViaWebSocket(
        message: String,
        quickActionId: String?
    ): Result<ChatMessage> {
        val ws = openClawWebSocket ?: return Result.failure(
            IllegalStateException("WebSocket not initialized")
        )
        
        val sessionKey = preferencesManager.getSessionKeySync()
        val result = ws.sendChatMessage(message, sessionKey)
        
        return result.fold(
            onSuccess = { reply ->
                // Strip markdown for clean display
                val cleanReply = TextUtils.stripMarkdown(reply)
                
                val botMessage = ChatMessage(
                    content = cleanReply,
                    isFromUser = false,
                    quickActionId = quickActionId
                )
                chatDao.insertMessage(botMessage)
                
                // Speak the response if TTS is enabled
                if (preferencesManager.getTtsEnabledSync() && context != null) {
                    try {
                        val tts = TtsManager.getInstance(context)
                        tts.speak(cleanReply)
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS failed: ${e.message}")
                    }
                }
                
                Result.success(botMessage)
            },
            onFailure = { error ->
                Log.e(TAG, "WebSocket send failed: ${error.message}", error)
                // Fallback to webhook on WebSocket failure
                sendViaWebhook(message, quickActionId)
            }
        )
    }
    
    private suspend fun sendViaWebhook(
        message: String,
        quickActionId: String?
    ): Result<ChatMessage> {
        val webhookUrl = preferencesManager.getWebhookUrlSync()
        val result = apiClient.sendMessage(webhookUrl, message, quickActionId)
        
        return result.fold(
            onSuccess = { response ->
                val reply = response.reply ?: response.error ?: "Geen antwoord ontvangen"
                // Strip markdown for clean display
                val cleanReply = TextUtils.stripMarkdown(reply)
                
                val botMessage = ChatMessage(
                    content = cleanReply,
                    isFromUser = false,
                    quickActionId = quickActionId
                )
                chatDao.insertMessage(botMessage)
                
                // Speak the response if TTS is enabled
                if (preferencesManager.getTtsEnabledSync() && context != null) {
                    try {
                        val tts = TtsManager.getInstance(context)
                        tts.speak(cleanReply)
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS failed: ${e.message}")
                    }
                }
                
                Result.success(botMessage)
            },
            onFailure = { error ->
                val errorMessage = ChatMessage(
                    content = "Fout: ${error.localizedMessage ?: "Onbekende fout"}",
                    isFromUser = false,
                    quickActionId = quickActionId
                )
                chatDao.insertMessage(errorMessage)
                Result.failure(error)
            }
        )
    }
    
    suspend fun clearHistory() {
        chatDao.clearAllMessages()
    }
    
    suspend fun deleteOldMessages(daysOld: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        chatDao.deleteOldMessages(cutoffTime)
    }
    
    suspend fun getSortedQuickActions(): List<QuickAction> {
        val usageMap = preferencesManager.getActionUsage()
        val lastUsedMap = preferencesManager.getActionLastUsed()
        
        return QuickActions.defaultActions.map { action ->
            action.copy(
                usageCount = usageMap[action.id] ?: 0,
                lastUsed = lastUsedMap[action.id] ?: 0
            )
        }.sortedWith(
            compareByDescending<QuickAction> { it.usageCount }
                .thenByDescending { it.lastUsed }
        )
    }
    
    fun cleanup() {
        openClawWebSocket?.cleanup()
        openClawWebSocket = null
    }
    
    suspend fun reinitializeWebSocket() {
        openClawWebSocket?.cleanup()
        openClawWebSocket = null
        if (preferencesManager.getUseOpenClawWebSocketSync()) {
            initializeOpenClawWebSocket()
        }
    }
}
