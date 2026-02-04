package com.mymate.auto.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * OpenClaw Gateway WebSocket Client
 * 
 * Implements the OpenClaw Gateway WebSocket protocol for real-time
 * bidirectional communication with the agent.
 * 
 * Protocol: ws://<gateway>:18789
 * - First frame must be a 'connect' request
 * - Then can send chat.send requests and receive events
 */
class OpenClawWebSocket(
    private val gatewayUrl: String = "ws://100.124.24.27:18789",
    private val authToken: String? = null
) {
    
    companion object {
        private const val TAG = "OpenClawWebSocket"
        private const val PROTOCOL_VERSION = 3
        private const val CLIENT_ID = "mymate-android-auto"
        private const val CLIENT_VERSION = "2.14"
    }
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestIdCounter = AtomicInteger(0)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _chatResponses = MutableSharedFlow<ChatResponse>()
    val chatResponses: SharedFlow<ChatResponse> = _chatResponses
    
    private val _agentEvents = MutableSharedFlow<AgentEvent>()
    val agentEvents: SharedFlow<AgentEvent> = _agentEvents
    
    private var isConnected = false
    private var sessionKey: String = "agent:main:main"
    
    enum class ConnectionState {
        CONNECTING,
        HANDSHAKING,
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        ERROR
    }
    
    data class ChatResponse(
        val sessionKey: String,
        val content: String,
        val isComplete: Boolean,
        val error: String? = null
    )
    
    data class AgentEvent(
        val type: String,
        val sessionKey: String?,
        val content: String?,
        val status: String?,
        val data: JsonObject? = null
    )
    
    fun connect(autoReconnect: Boolean = true) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.HANDSHAKING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        isConnected = false
        
        val wsUrl = gatewayUrl.replace("http://", "ws://").replace("https://", "wss://")
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        Log.d(TAG, "Connecting to OpenClaw Gateway: $wsUrl")
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, waiting for connect.challenge")
                _connectionState.value = ConnectionState.HANDSHAKING
                reconnectJob?.cancel()
                // Don't send connect yet - wait for connect.challenge event from Gateway
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: ${text.take(500)}...")
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                _connectionState.value = ConnectionState.DISCONNECTED
                
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                isConnected = false
                _connectionState.value = ConnectionState.ERROR
                
                // Reject pending requests
                pendingRequests.values.forEach { 
                    it.completeExceptionally(t) 
                }
                pendingRequests.clear()
                
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun sendConnectHandshake(ws: WebSocket) {
        val connectId = "connect-${requestIdCounter.incrementAndGet()}"
        
        val deviceId = "android-auto-${android.os.Build.MODEL.replace(" ", "-")}"
        
        val connectRequest = buildJsonObject {
            put("type", "req")
            put("id", connectId)
            put("method", "connect")
            putObject("params") {
                put("minProtocol", PROTOCOL_VERSION)
                put("maxProtocol", PROTOCOL_VERSION)
                putObject("client") {
                    put("id", CLIENT_ID)
                    put("displayName", "MyMate Android Auto")
                    put("version", CLIENT_VERSION)
                    put("platform", "android ${android.os.Build.VERSION.SDK_INT}")
                    put("mode", "ui")
                    put("instanceId", deviceId)
                }
                put("role", "operator")
                putArray("scopes") {
                    add("operator.read")
                    add("operator.write")
                }
                putArray("caps") {}
                putArray("commands") {}
                putObject("permissions") {}
                if (authToken != null) {
                    putObject("auth") {
                        put("token", authToken)
                    }
                }
                put("locale", "nl-NL")
                put("userAgent", "$CLIENT_ID/$CLIENT_VERSION")
                putObject("device") {
                    put("id", deviceId)
                }
            }
        }
        
        val json = gson.toJson(connectRequest)
        Log.d(TAG, "Sending connect: $json")
        ws.send(json)
    }
    
    private fun handleMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "res" -> handleResponse(json)
                "event" -> handleEvent(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }
    
    private fun handleResponse(json: JsonObject) {
        val id = json.get("id")?.asString ?: return
        val ok = json.get("ok")?.asBoolean ?: false
        
        // Check if this is the connect response
        if (id.startsWith("connect-")) {
            if (ok) {
                val payload = json.getAsJsonObject("payload")
                val payloadType = payload?.get("type")?.asString
                if (payloadType == "hello-ok") {
                    Log.d(TAG, "Connected successfully to OpenClaw Gateway")
                    isConnected = true
                    _connectionState.value = ConnectionState.CONNECTED
                }
            } else {
                val error = json.getAsJsonObject("error")?.get("message")?.asString ?: "Connection failed"
                Log.e(TAG, "Connect failed: $error")
                _connectionState.value = ConnectionState.ERROR
            }
            return
        }
        
        // Handle other responses
        val pending = pendingRequests.remove(id)
        if (pending != null) {
            if (ok) {
                pending.complete(json.getAsJsonObject("payload") ?: JsonObject())
            } else {
                val error = json.getAsJsonObject("error")?.get("message")?.asString ?: "Request failed"
                pending.completeExceptionally(RuntimeException(error))
            }
        }
        
        // Also emit chat responses for chat.send results
        if (id.startsWith("chat-")) {
            if (ok) {
                val payload = json.getAsJsonObject("payload")
                val content = payload?.get("text")?.asString 
                    ?: payload?.get("content")?.asString 
                    ?: ""
                scope.launch {
                    _chatResponses.emit(ChatResponse(
                        sessionKey = sessionKey,
                        content = content,
                        isComplete = true
                    ))
                }
            } else {
                val error = json.getAsJsonObject("error")?.get("message")?.asString ?: "Chat failed"
                scope.launch {
                    _chatResponses.emit(ChatResponse(
                        sessionKey = sessionKey,
                        content = "",
                        isComplete = true,
                        error = error
                    ))
                }
            }
        }
    }
    
    private fun handleEvent(json: JsonObject) {
        val event = json.get("event")?.asString ?: return
        val payload = json.getAsJsonObject("payload")
        
        Log.d(TAG, "Event: $event")
        
        when (event) {
            "connect.challenge" -> {
                // Gateway sends challenge, now we can send our connect request
                Log.d(TAG, "Received connect.challenge, sending handshake")
                webSocket?.let { sendConnectHandshake(it) }
            }
            "tick" -> {
                // Heartbeat from server, connection is alive
                Log.d(TAG, "Tick received")
            }
            "agent" -> {
                // Agent activity event
                val status = payload?.get("status")?.asString
                val sk = payload?.get("sessionKey")?.asString
                scope.launch {
                    _agentEvents.emit(AgentEvent(
                        type = "agent",
                        sessionKey = sk,
                        content = null,
                        status = status,
                        data = payload
                    ))
                }
            }
            "chat" -> {
                // Chat message event (streaming or final)
                val sk = payload?.get("sessionKey")?.asString ?: sessionKey
                val content = payload?.get("text")?.asString 
                    ?: payload?.get("content")?.asString 
                    ?: ""
                val isComplete = payload?.get("complete")?.asBoolean ?: true
                scope.launch {
                    _chatResponses.emit(ChatResponse(
                        sessionKey = sk,
                        content = content,
                        isComplete = isComplete
                    ))
                }
            }
            "chat.chunk" -> {
                // Streaming chunk
                val sk = payload?.get("sessionKey")?.asString ?: sessionKey
                val chunk = payload?.get("chunk")?.asString ?: ""
                scope.launch {
                    _chatResponses.emit(ChatResponse(
                        sessionKey = sk,
                        content = chunk,
                        isComplete = false
                    ))
                }
            }
            "presence" -> {
                // Presence update
                Log.d(TAG, "Presence update received")
            }
            else -> {
                Log.d(TAG, "Unknown event: $event")
            }
        }
    }
    
    /**
     * Send a chat message to the agent
     */
    suspend fun sendChatMessage(message: String, sk: String = sessionKey): Result<String> {
        if (!isConnected) {
            return Result.failure(IllegalStateException("Not connected to Gateway"))
        }
        
        val requestId = "chat-${requestIdCounter.incrementAndGet()}"
        val idempotencyKey = UUID.randomUUID().toString()
        
        val request = buildJsonObject {
            put("type", "req")
            put("id", requestId)
            put("method", "chat.send")
            putObject("params") {
                put("sessionKey", sk)
                put("text", message)
                put("idempotencyKey", idempotencyKey)
            }
        }
        
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred
        
        val json = gson.toJson(request)
        Log.d(TAG, "Sending chat.send: $json")
        
        return try {
            webSocket?.send(json) ?: return Result.failure(IllegalStateException("WebSocket is null"))
            
            // Wait for response with timeout
            withTimeout(120_000) {
                val response = deferred.await()
                val text = response.get("text")?.asString 
                    ?: response.get("content")?.asString 
                    ?: response.toString()
                Result.success(text)
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Result.failure(e)
        }
    }
    
    /**
     * Get chat history
     */
    suspend fun getChatHistory(sk: String = sessionKey, limit: Int = 50): Result<List<Map<String, Any>>> {
        if (!isConnected) {
            return Result.failure(IllegalStateException("Not connected to Gateway"))
        }
        
        val requestId = "history-${requestIdCounter.incrementAndGet()}"
        
        val request = buildJsonObject {
            put("type", "req")
            put("id", requestId)
            put("method", "chat.history")
            putObject("params") {
                put("sessionKey", sk)
                put("limit", limit)
            }
        }
        
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred
        
        val json = gson.toJson(request)
        Log.d(TAG, "Sending chat.history: $json")
        
        return try {
            webSocket?.send(json) ?: return Result.failure(IllegalStateException("WebSocket is null"))
            
            withTimeout(30_000) {
                val response = deferred.await()
                val messages = response.getAsJsonArray("messages")?.map { 
                    gson.fromJson(it, Map::class.java) as Map<String, Any>
                } ?: emptyList()
                Result.success(messages)
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Result.failure(e)
        }
    }
    
    /**
     * Check gateway health
     */
    suspend fun checkHealth(): Result<Boolean> {
        if (!isConnected) {
            return Result.failure(IllegalStateException("Not connected to Gateway"))
        }
        
        val requestId = "health-${requestIdCounter.incrementAndGet()}"
        
        val request = buildJsonObject {
            put("type", "req")
            put("id", requestId)
            put("method", "health")
        }
        
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred
        
        val json = gson.toJson(request)
        
        return try {
            webSocket?.send(json) ?: return Result.failure(IllegalStateException("WebSocket is null"))
            
            withTimeout(10_000) {
                val response = deferred.await()
                val ok = response.get("ok")?.asBoolean ?: false
                Result.success(ok)
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Result.failure(e)
        }
    }
    
    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected = false
        _connectionState.value = ConnectionState.DISCONNECTED
        pendingRequests.clear()
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
    
    fun setSessionKey(key: String) {
        sessionKey = key
    }
    
    // Helper to build JSON objects
    private fun buildJsonObject(builder: JsonObjectBuilder.() -> Unit): JsonObject {
        return JsonObjectBuilder().apply(builder).build()
    }
    
    private class JsonObjectBuilder {
        private val obj = JsonObject()
        
        fun put(key: String, value: String) = obj.addProperty(key, value)
        fun put(key: String, value: Int) = obj.addProperty(key, value)
        fun put(key: String, value: Boolean) = obj.addProperty(key, value)
        
        fun putObject(key: String, builder: JsonObjectBuilder.() -> Unit) {
            obj.add(key, JsonObjectBuilder().apply(builder).build())
        }
        
        fun putArray(key: String, builder: JsonArrayBuilder.() -> Unit = {}) {
            obj.add(key, JsonArrayBuilder().apply(builder).build())
        }
        
        fun build() = obj
    }
    
    private class JsonArrayBuilder {
        private val arr = com.google.gson.JsonArray()
        
        fun add(value: String) = arr.add(value)
        fun add(value: Int) = arr.add(value)
        fun add(value: Boolean) = arr.add(value)
        
        fun build() = arr
    }
}
