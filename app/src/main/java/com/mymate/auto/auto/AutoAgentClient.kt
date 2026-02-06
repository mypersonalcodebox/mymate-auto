package com.mymate.auto.auto

import android.content.Context
import android.util.Log
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.remote.OpenClawWebSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Singleton client for Android Auto screens to communicate with OpenClaw Gateway.
 * 
 * Uses WebSocket for real-time responses since the HTTP webhook (/hooks/agent)
 * only returns 202 Accepted and delivers responses to Telegram, not inline.
 * 
 * Usage:
 *   val client = AutoAgentClient.getInstance(context)
 *   val result = client.sendMessage("Hello")
 *   result.onSuccess { reply -> ... }
 *   result.onFailure { error -> ... }
 */
class AutoAgentClient private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoAgentClient"
        
        @Volatile
        private var instance: AutoAgentClient? = null
        
        fun getInstance(context: Context): AutoAgentClient {
            return instance ?: synchronized(this) {
                instance ?: AutoAgentClient(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val preferencesManager = PreferencesManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionMutex = Mutex()
    
    private var webSocket: OpenClawWebSocket? = null
    
    private val _connectionState = MutableStateFlow(OpenClawWebSocket.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<OpenClawWebSocket.ConnectionState> = _connectionState
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    
    init {
        // Start connection in background
        scope.launch {
            connect()
        }
    }
    
    /**
     * Connect to the OpenClaw Gateway via WebSocket
     */
    suspend fun connect(): Boolean = connectionMutex.withLock {
        if (_connectionState.value == OpenClawWebSocket.ConnectionState.CONNECTED) {
            return true
        }
        
        return try {
            val gatewayUrl = preferencesManager.getGatewayUrlSync()
            val token = preferencesManager.getGatewayTokenSync()
            
            Log.d(TAG, "Connecting to gateway: $gatewayUrl")
            
            webSocket?.cleanup()
            webSocket = OpenClawWebSocket(
                gatewayUrl = gatewayUrl,
                authToken = token
            ).apply {
                // Monitor connection state
                scope.launch {
                    connectionState.collect { state ->
                        Log.d(TAG, "Connection state: $state")
                        _connectionState.value = state
                    }
                }
                
                // Monitor errors
                scope.launch {
                    connectionErrors.collect { error ->
                        Log.e(TAG, "Connection error: ${error.userMessage}")
                        _lastError.value = error.userMessage
                    }
                }
                
                connect(autoReconnect = true)
            }
            
            // Wait for connection (up to 10 seconds)
            withTimeoutOrNull(10_000) {
                while (_connectionState.value != OpenClawWebSocket.ConnectionState.CONNECTED) {
                    if (_connectionState.value == OpenClawWebSocket.ConnectionState.ERROR) {
                        return@withTimeoutOrNull false
                    }
                    delay(100)
                }
                true
            } ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _lastError.value = "Verbinding mislukt: ${e.message}"
            false
        }
    }
    
    /**
     * Send a message and wait for the response.
     * 
     * This uses WebSocket to get real-time responses, unlike the HTTP webhook
     * which only triggers async processing.
     * 
     * @param message The message to send
     * @param sessionKey Optional session key for conversation context
     * @param timeoutMs Timeout in milliseconds (default 120 seconds for long responses)
     * @return Result with the response text or error
     */
    suspend fun sendMessage(
        message: String,
        sessionKey: String = "agent:main:mymate",  // Same as phone app for shared conversation
        timeoutMs: Long = 120_000
    ): Result<String> {
        // Ensure connected
        if (_connectionState.value != OpenClawWebSocket.ConnectionState.CONNECTED) {
            val connected = connect()
            if (!connected) {
                return Result.failure(
                    IllegalStateException(_lastError.value ?: "Niet verbonden met gateway")
                )
            }
        }
        
        val ws = webSocket ?: return Result.failure(
            IllegalStateException("WebSocket niet geïnitialiseerd")
        )
        
        return try {
            Log.d(TAG, "Sending message: ${message.take(50)}...")
            ws.setSessionKey(sessionKey)
            
            withTimeout(timeoutMs) {
                ws.sendChatMessage(message, sessionKey)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Request timed out")
            Result.failure(RuntimeException("Timeout - geen antwoord binnen ${timeoutMs/1000}s"))
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if connected to the gateway
     */
    fun isConnected(): Boolean {
        return _connectionState.value == OpenClawWebSocket.ConnectionState.CONNECTED
    }
    
    /**
     * Get connection status text for UI
     */
    fun getStatusText(): String {
        return when (_connectionState.value) {
            OpenClawWebSocket.ConnectionState.CONNECTED -> "Verbonden"
            OpenClawWebSocket.ConnectionState.CONNECTING -> "Verbinden..."
            OpenClawWebSocket.ConnectionState.HANDSHAKING -> "Handshake..."
            OpenClawWebSocket.ConnectionState.RECONNECTING -> "Opnieuw verbinden..."
            OpenClawWebSocket.ConnectionState.DISCONNECTED -> "Niet verbonden"
            OpenClawWebSocket.ConnectionState.ERROR -> _lastError.value ?: "Fout"
        }
    }
    
    /**
     * Force reconnect (e.g., after settings change)
     */
    suspend fun reconnect() {
        connectionMutex.withLock {
            webSocket?.cleanup()
            webSocket = null
            _connectionState.value = OpenClawWebSocket.ConnectionState.DISCONNECTED
            _lastError.value = null
        }
        connect()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.launch {
            connectionMutex.withLock {
                webSocket?.cleanup()
                webSocket = null
            }
        }
    }
}
