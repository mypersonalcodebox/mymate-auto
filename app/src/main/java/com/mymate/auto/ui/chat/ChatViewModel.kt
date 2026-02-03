package com.mymate.auto.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ChatMessage
import com.mymate.auto.data.model.QuickAction
import com.mymate.auto.data.remote.MyMateApiClient
import com.mymate.auto.data.remote.WebSocketManager
import com.mymate.auto.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val preferencesManager = PreferencesManager(application)
    private val apiClient = MyMateApiClient()
    private val webSocketManager = WebSocketManager()
    
    private val repository = ChatRepository(
        database.chatDao(),
        apiClient,
        preferencesManager
    )
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    val messages: Flow<List<ChatMessage>> = repository.getAllMessages()
    val connectionState = webSocketManager.connectionState
    
    init {
        // Load quick actions sorted by usage
        viewModelScope.launch {
            val sortedActions = repository.getSortedQuickActions()
            _uiState.update { it.copy(quickActions = sortedActions) }
        }
        
        // Monitor WebSocket messages
        viewModelScope.launch {
            webSocketManager.incomingMessages.collect { message ->
                when (message.type) {
                    "notification", "message" -> {
                        message.content?.let { content ->
                            // Add incoming message to chat
                            database.chatDao().insertMessage(
                                ChatMessage(
                                    content = content,
                                    isFromUser = false
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Connect WebSocket
        viewModelScope.launch {
            preferencesManager.webSocketEnabled.collect { enabled ->
                if (enabled) {
                    webSocketManager.connect()
                } else {
                    webSocketManager.disconnect()
                }
            }
        }
    }
    
    fun sendMessage(message: String, quickActionId: String? = null) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = repository.sendMessage(message, quickActionId)
            
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(isLoading = false)
                    }
                    // Refresh quick actions sorting
                    val sortedActions = repository.getSortedQuickActions()
                    _uiState.update { it.copy(quickActions = sortedActions) }
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
    
    fun executeQuickAction(action: QuickAction) {
        if (action.query.isNotEmpty()) {
            sendMessage(action.query, action.id)
        } else {
            // For free chat, just increment usage
            viewModelScope.launch {
                preferencesManager.incrementActionUsage(action.id)
            }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun reconnectWebSocket() {
        webSocketManager.connect(autoReconnect = true)
    }
    
    override fun onCleared() {
        super.onCleared()
        webSocketManager.cleanup()
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val quickActions: List<QuickAction> = emptyList()
)
