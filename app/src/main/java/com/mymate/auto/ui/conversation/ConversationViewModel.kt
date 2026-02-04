package com.mymate.auto.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ConversationMessage
import com.mymate.auto.data.remote.MyMateApiClient
import com.mymate.auto.data.remote.OpenClawWebSocket
import com.mymate.auto.data.repository.ConversationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Conversation mode - maintains context across messages
 * Uses a dedicated session key for conversation continuity
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        // Dedicated session key for conversations - separate from quick actions
        const val CONVERSATION_SESSION_KEY = "agent:main:mymate:conversation"
    }
    
    private val database = AppDatabase.getInstance(application)
    private val preferencesManager = PreferencesManager(application)
    private val apiClient = MyMateApiClient()
    
    private val repository = ConversationRepository(
        database.conversationDao(),
        apiClient,
        preferencesManager,
        application.applicationContext
    )
    
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()
    
    val messages: Flow<List<ConversationMessage>> = repository.getAllMessages()
    
    val connectionState: StateFlow<OpenClawWebSocket.ConnectionState> = repository.webSocketState
    
    // Current conversation topic (extracted from first message or set by user)
    private val _conversationTopic = MutableStateFlow<String?>(null)
    val conversationTopic: StateFlow<String?> = _conversationTopic.asStateFlow()
    
    init {
        // Monitor connection state
        viewModelScope.launch {
            repository.webSocketState.collect { state ->
                _uiState.update { it.copy(connectionStatus = state.toDisplayString()) }
            }
        }
        
        // Try to determine topic from existing messages
        viewModelScope.launch {
            messages.collect { msgs ->
                if (_conversationTopic.value == null && msgs.isNotEmpty()) {
                    val firstUserMessage = msgs.lastOrNull { it.isFromUser }
                    firstUserMessage?.let {
                        _conversationTopic.value = extractTopic(it.content)
                    }
                }
                _uiState.update { it.copy(messageCount = msgs.size) }
            }
        }
    }
    
    /**
     * Send a message in the conversation context
     * The system prompt will instruct the AI to maintain context and ask for clarification when needed
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Build context-aware message
            val contextMessage = buildContextMessage(message)
            
            val result = repository.sendMessage(contextMessage)
            
            result.fold(
                onSuccess = { response ->
                    _uiState.update { it.copy(isLoading = false) }
                    
                    // Update topic if this was the first message
                    if (_conversationTopic.value == null) {
                        _conversationTopic.value = extractTopic(message)
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.localizedMessage ?: "Onbekende fout"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Build a context-aware message
     * For the first message, include instructions for contextual conversation
     */
    private suspend fun buildContextMessage(userMessage: String): String {
        val currentMessages = messages.first()
        
        // If this is the first message, add context instructions
        return if (currentMessages.isEmpty()) {
            """
            |[CONVERSATION MODE - Maintain context throughout this conversation]
            |$userMessage
            """.trimMargin()
        } else {
            userMessage
        }
    }
    
    /**
     * Extract a short topic from the message
     */
    private fun extractTopic(message: String): String {
        // Take first few words or sentence
        val words = message.split(" ").take(5)
        return if (words.size >= 5) {
            words.joinToString(" ") + "..."
        } else {
            message.take(50).let { if (message.length > 50) "$it..." else it }
        }
    }
    
    /**
     * Start a new conversation - clears history and resets topic
     */
    fun startNewConversation() {
        viewModelScope.launch {
            repository.clearHistory()
            _conversationTopic.value = null
            _uiState.update { it.copy(messageCount = 0, error = null) }
        }
    }
    
    /**
     * Set conversation topic manually
     */
    fun setTopic(topic: String) {
        _conversationTopic.value = topic
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun reconnectWebSocket() {
        repository.connectWebSocket()
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}

data class ConversationUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionStatus: String = "Niet verbonden",
    val messageCount: Int = 0
)

private fun OpenClawWebSocket.ConnectionState.toDisplayString(): String = when (this) {
    OpenClawWebSocket.ConnectionState.CONNECTING -> "Verbinden..."
    OpenClawWebSocket.ConnectionState.HANDSHAKING -> "Authenticeren..."
    OpenClawWebSocket.ConnectionState.CONNECTED -> "🦞 Verbonden"
    OpenClawWebSocket.ConnectionState.DISCONNECTED -> "Niet verbonden"
    OpenClawWebSocket.ConnectionState.RECONNECTING -> "Herverbinden..."
    OpenClawWebSocket.ConnectionState.ERROR -> "Verbindingsfout"
}
