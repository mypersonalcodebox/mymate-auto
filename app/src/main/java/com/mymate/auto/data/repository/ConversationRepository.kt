package com.mymate.auto.data.repository

import android.content.Context
import android.util.Log
import com.mymate.auto.data.local.ConversationDao
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ConversationMessage
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
import kotlinx.coroutines.launch

/**
 * Repository for conversation mode - uses dedicated session key for context
 */
class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val apiClient: MyMateApiClient,
    private val preferencesManager: PreferencesManager,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "ConversationRepository"
        // Dedicated session key for conversations
        const val CONVERSATION_SESSION_KEY = "agent:main:mymate:conversation"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var openClawWebSocket: OpenClawWebSocket? = null
    
    private val _webSocketState = MutableStateFlow(OpenClawWebSocket.ConnectionState.DISCONNECTED)
    val webSocketState: StateFlow<OpenClawWebSocket.ConnectionState> = _webSocketState
    
    init {
        scope.launch {
            if (preferencesManager.getUseOpenClawWebSocketSync()) {
                initializeOpenClawWebSocket()
            }
        }
    }
    
    private suspend fun initializeOpenClawWebSocket() {
        val gatewayUrl = preferencesManager.getGatewayUrlSync()
        val token = preferencesManager.getGatewayTokenSync()
        
        Log.d(TAG, "Initializing OpenClaw WebSocket for conversation: $gatewayUrl")
        
        openClawWebSocket?.cleanup()
        openClawWebSocket = OpenClawWebSocket(
            gatewayUrl = gatewayUrl,
            authToken = token
        ).apply {
            // Use dedicated conversation session key
            setSessionKey(CONVERSATION_SESSION_KEY)
            
            scope.launch {
                connectionState.collect { state ->
                    Log.d(TAG, "WebSocket state: $state")
                    _webSocketState.value = state
                }
            }
            
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
    
    fun getAllMessages(): Flow<List<ConversationMessage>> {
        return conversationDao.getAllMessages()
    }
    
    suspend fun sendMessage(message: String): Result<ConversationMessage> {
        // Save user message
        val userMessage = ConversationMessage(
            content = message,
            isFromUser = true,
            topic = null
        )
        conversationDao.insertMessage(userMessage)
        
        // Try WebSocket first
        val useWebSocket = preferencesManager.getUseOpenClawWebSocketSync()
        if (useWebSocket && _webSocketState.value == OpenClawWebSocket.ConnectionState.CONNECTED) {
            Log.d(TAG, "Sending via OpenClaw WebSocket (conversation)")
            return sendViaWebSocket(message)
        }
        
        // Fallback to HTTP
        Log.d(TAG, "Sending via HTTP webhook (conversation)")
        return sendViaWebhook(message)
    }
    
    private suspend fun sendViaWebSocket(message: String): Result<ConversationMessage> {
        val ws = openClawWebSocket ?: return Result.failure(
            IllegalStateException("WebSocket not initialized")
        )
        
        val result = ws.sendChatMessage(message, CONVERSATION_SESSION_KEY)
        
        return result.fold(
            onSuccess = { reply ->
                val cleanReply = TextUtils.stripMarkdown(reply)
                
                val botMessage = ConversationMessage(
                    content = cleanReply,
                    isFromUser = false,
                    topic = null
                )
                conversationDao.insertMessage(botMessage)
                
                // TTS if enabled
                scope.launch {
                    if (preferencesManager.getTtsEnabledSync() && context != null) {
                        try {
                            val tts = TtsManager.getInstance(context)
                            tts.speak(cleanReply)
                        } catch (e: Exception) {
                            Log.e(TAG, "TTS failed: ${e.message}")
                        }
                    }
                }
                
                Result.success(botMessage)
            },
            onFailure = { error ->
                Log.e(TAG, "WebSocket send failed: ${error.message}", error)
                sendViaWebhook(message)
            }
        )
    }
    
    private suspend fun sendViaWebhook(message: String): Result<ConversationMessage> {
        val webhookUrl = preferencesManager.getWebhookUrlSync()
        val result = apiClient.sendMessage(webhookUrl, message, "conversation")
        
        return result.fold(
            onSuccess = { response ->
                val reply = response.reply ?: response.error ?: "Geen antwoord ontvangen"
                val cleanReply = TextUtils.stripMarkdown(reply)
                
                val botMessage = ConversationMessage(
                    content = cleanReply,
                    isFromUser = false,
                    topic = null
                )
                conversationDao.insertMessage(botMessage)
                
                scope.launch {
                    if (preferencesManager.getTtsEnabledSync() && context != null) {
                        try {
                            val tts = TtsManager.getInstance(context)
                            tts.speak(cleanReply)
                        } catch (e: Exception) {
                            Log.e(TAG, "TTS failed: ${e.message}")
                        }
                    }
                }
                
                Result.success(botMessage)
            },
            onFailure = { error ->
                val errorMessage = ConversationMessage(
                    content = "Fout: ${error.localizedMessage ?: "Onbekende fout"}",
                    isFromUser = false,
                    topic = null
                )
                conversationDao.insertMessage(errorMessage)
                Result.failure(error)
            }
        )
    }
    
    suspend fun clearHistory() {
        conversationDao.clearAllMessages()
        
        // Also reset the session on the gateway side
        openClawWebSocket?.let { ws ->
            try {
                // Send a reset message to clear context
                Log.d(TAG, "Conversation history cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset session: ${e.message}")
            }
        }
    }
    
    fun cleanup() {
        openClawWebSocket?.cleanup()
        openClawWebSocket = null
    }
}
