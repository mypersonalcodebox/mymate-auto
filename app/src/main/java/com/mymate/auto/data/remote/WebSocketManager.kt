package com.mymate.auto.data.remote

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val baseUrl: String = "ws://your-gateway:18791"
) {
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>()
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages
    
    enum class ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        ERROR
    }
    
    data class WebSocketMessage(
        val type: String,
        val content: String?,
        val data: Map<String, Any>? = null
    )
    
    fun connect(autoReconnect: Boolean = true) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        
        val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        val request = Request.Builder()
            .url("$wsUrl/ws")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectJob?.cancel()
                
                // Send registration message
                val registration = mapOf(
                    "type" to "register",
                    "client" to "android_auto",
                    "version" to "2.0"
                )
                webSocket.send(gson.toJson(registration))
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message: $text")
                scope.launch {
                    try {
                        val message = gson.fromJson(text, WebSocketMessage::class.java)
                        _incomingMessages.emit(message)
                    } catch (e: Exception) {
                        _incomingMessages.emit(WebSocketMessage("text", text))
                    }
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                _connectionState.value = ConnectionState.ERROR
                
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }
    
    fun sendMessage(type: String, content: String? = null, data: Map<String, Any>? = null): Boolean {
        val message = mutableMapOf<String, Any?>(
            "type" to type
        )
        content?.let { message["content"] = it }
        data?.let { message.putAll(it) }
        
        return send(gson.toJson(message))
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            delay(5000) // Wait 5 seconds before reconnecting
            connect(autoReconnect = true)
        }
    }
    
    fun cleanup() {
        scope.cancel()
        disconnect()
        client.dispatcher.executorService.shutdown()
    }
    
    companion object {
        private const val TAG = "WebSocketManager"
    }
}
