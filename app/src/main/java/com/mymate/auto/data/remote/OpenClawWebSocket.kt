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
import kotlinx.coroutines.flow.first
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random

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
    private val gatewayUrl: String = "ws://your-gateway:18789",
    private val authToken: String? = null
) {
    
    companion object {
        private const val TAG = "OpenClawWebSocket"
        private const val PROTOCOL_VERSION = 3
        private const val CLIENT_ID = "openclaw-android"
        private const val CLIENT_VERSION = "2.37"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val BASE_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    @Volatile
    private var webSocket: WebSocket? = null
    private val webSocketLock = Any()
    private val gson = Gson()
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestIdCounter = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private var reconnectAttempt = AtomicInteger(0)
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _chatResponses = MutableSharedFlow<ChatResponse>(replay = 1, extraBufferCapacity = 10)
    val chatResponses: SharedFlow<ChatResponse> = _chatResponses
    
    private val _agentEvents = MutableSharedFlow<AgentEvent>()
    val agentEvents: SharedFlow<AgentEvent> = _agentEvents
    
    private val _connectionErrors = MutableSharedFlow<ConnectionError>(replay = 1)
    val connectionErrors: SharedFlow<ConnectionError> = _connectionErrors
    
    private val isConnected = AtomicBoolean(false)
    @Volatile
    private var sessionKey: String = "agent:main:main"
    
    enum class ConnectionState {
        CONNECTING,
        HANDSHAKING,
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        ERROR
    }
    
    /**
     * Connection error details for UI display
     */
    data class ConnectionError(
        val code: Int?,
        val reason: String,
        val userMessage: String,
        val isAuthError: Boolean,
        val isRecoverable: Boolean
    )
    
    data class ChatResponse(
        val sessionKey: String,
        val content: String,
        val isComplete: Boolean,
        val error: String? = null,
        val runId: String? = null
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
        isConnected.set(false)
        reconnectAttempt.set(0)
        
        var wsUrl = gatewayUrl.replace("http://", "ws://").replace("https://", "wss://")
        
        // Add token as URL parameter for auth
        if (authToken != null) {
            val separator = if (wsUrl.contains("?")) "&" else "?"
            wsUrl = "$wsUrl${separator}token=$authToken"
        }
        
        val requestBuilder = Request.Builder().url(wsUrl)
        
        // Also add as Authorization header
        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
        }
        
        val request = requestBuilder.build()
        
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
                Log.d(TAG, "WebSocket closing: code=$code, reason='$reason'")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: code=$code, reason='$reason'")
                isConnected.set(false)
                
                // Parse close code and determine error type
                val (userMessage, isAuthError, isRecoverable) = parseCloseCode(code, reason)
                
                if (code != 1000) { // 1000 = normal closure
                    Log.w(TAG, "WebSocket closed abnormally: $userMessage (code=$code)")
                    _connectionState.value = ConnectionState.ERROR
                    
                    scope.launch {
                        _connectionErrors.emit(ConnectionError(
                            code = code,
                            reason = reason,
                            userMessage = userMessage,
                            isAuthError = isAuthError,
                            isRecoverable = isRecoverable
                        ))
                    }
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                
                // Only reconnect for recoverable errors, NOT auth failures
                if (autoReconnect && isRecoverable && !isAuthError) {
                    scheduleReconnect()
                } else if (isAuthError) {
                    Log.w(TAG, "Authentication error - not reconnecting. Check your token.")
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val responseCode = response?.code
                val responseMessage = response?.message ?: t.message ?: "Unknown error"
                
                Log.e(TAG, "WebSocket failure: ${t.message} (HTTP $responseCode: $responseMessage)", t)
                isConnected.set(false)
                _connectionState.value = ConnectionState.ERROR
                
                // Check if this is an auth error from HTTP response
                val isAuthError = responseCode in listOf(401, 403)
                val userMessage = when {
                    isAuthError -> "Token onjuist of verlopen"
                    responseCode == 404 -> "Gateway niet gevonden"
                    t is java.net.UnknownHostException -> "Server niet bereikbaar - controleer adres"
                    t is java.net.ConnectException -> "Kan niet verbinden met server"
                    t is java.net.SocketTimeoutException -> "Verbinding timeout"
                    else -> "Verbindingsfout: ${t.message}"
                }
                
                scope.launch {
                    _connectionErrors.emit(ConnectionError(
                        code = responseCode,
                        reason = responseMessage,
                        userMessage = userMessage,
                        isAuthError = isAuthError,
                        isRecoverable = !isAuthError
                    ))
                }
                
                // Reject pending requests
                val pending = pendingRequests.values.toList()
                pendingRequests.clear()
                pending.forEach { it.completeExceptionally(t) }
                
                // Don't reconnect on auth errors
                if (autoReconnect && !isAuthError) {
                    scheduleReconnect()
                } else if (isAuthError) {
                    Log.w(TAG, "Authentication error - not reconnecting. Check your token.")
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
                // Note: device object removed - requires full signing which we don't implement
            }
        }
        
        val json = gson.toJson(connectRequest)
        Log.d(TAG, "Sending connect: $json")
        ws.send(json)
    }
    
    private fun handleMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            
            // Gateway sends events directly as { "event": "...", ... }
            // Check for this format first
            if (json.has("event") && !json.has("type")) {
                handleEvent(json)
                return
            }
            
            // Wrapped format: { "type": "event"|"res", ... }
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
                    Log.i(TAG, "✓ Connected successfully to OpenClaw Gateway")
                    isConnected.set(true)
                    reconnectAttempt.set(0)
                    _connectionState.value = ConnectionState.CONNECTED
                }
            } else {
                val errorObj = json.getAsJsonObject("error")
                val errorCode = errorObj?.get("code")?.asString ?: "UNKNOWN"
                val errorMessage = errorObj?.get("message")?.asString ?: "Connection failed"
                Log.e(TAG, "✗ Connect failed: [$errorCode] $errorMessage")
                _connectionState.value = ConnectionState.ERROR
                
                // Check if it's an auth error
                val isAuthError = errorCode in listOf("AUTH_FAILED", "INVALID_TOKEN", "UNAUTHORIZED", "FORBIDDEN") ||
                                  errorMessage.lowercase().contains("token") ||
                                  errorMessage.lowercase().contains("auth")
                
                val userMessage = if (isAuthError) {
                    "Token onjuist of geen toegang"
                } else {
                    errorMessage
                }
                
                scope.launch {
                    _connectionErrors.emit(ConnectionError(
                        code = null,
                        reason = errorMessage,
                        userMessage = userMessage,
                        isAuthError = isAuthError,
                        isRecoverable = !isAuthError
                    ))
                }
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
                // Chat message event - OpenClaw sends: { runId, state, message: { content: [{ text }] } }
                val sk = payload?.get("sessionKey")?.asString ?: sessionKey
                val runId = payload?.get("runId")?.asString
                val state = payload?.get("state")?.asString ?: "final"
                val isComplete = state == "final"
                val isError = state == "error"
                
                // Extract text from nested message.content[0].text structure
                var content = ""
                val message = payload?.getAsJsonObject("message")
                if (message != null) {
                    val contentArray = message.getAsJsonArray("content")
                    if (contentArray != null && contentArray.size() > 0) {
                        val firstContent = contentArray[0].asJsonObject
                        content = firstContent?.get("text")?.asString ?: ""
                    }
                }
                
                val errorMsg = if (isError) payload?.get("errorMessage")?.asString else null
                
                // Only emit final responses (skip deltas to avoid duplicates)
                if (isComplete && content.isNotEmpty()) {
                    Log.d(TAG, "Chat response received [runId=$runId]: ${content.take(50)}...")
                    scope.launch {
                        _chatResponses.emit(ChatResponse(
                            sessionKey = sk,
                            content = content,
                            isComplete = true,
                            error = errorMsg,
                            runId = runId
                        ))
                    }
                } else if (isError) {
                    Log.e(TAG, "Chat error [runId=$runId]: $errorMsg")
                    scope.launch {
                        _chatResponses.emit(ChatResponse(
                            sessionKey = sk,
                            content = "",
                            isComplete = true,
                            error = errorMsg ?: "Unknown error",
                            runId = runId
                        ))
                    }
                }
            }
            "presence" -> {
                // Presence update
                Log.d(TAG, "Presence update received")
            }
            "memory_sync" -> {
                // Memory sync from gateway - save to local database
                Log.d(TAG, "Memory sync received")
                payload?.let { p ->
                    scope.launch {
                        _agentEvents.emit(AgentEvent(
                            type = "memory_sync",
                            sessionKey = null,
                            content = p.toString(),
                            status = "sync",
                            data = p
                        ))
                    }
                }
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
        if (!isConnected.get()) {
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
                put("message", message)
                put("idempotencyKey", idempotencyKey)
            }
        }
        
        val ackDeferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = ackDeferred
        
        val json = gson.toJson(request)
        Log.d(TAG, "Sending chat.send: $json")
        
        return try {
            webSocket?.send(json) ?: return Result.failure(IllegalStateException("WebSocket is null"))
            
            // Wait for acknowledgment to get the runId
            val ackPayload = withTimeout(30_000) {
                ackDeferred.await()
            }
            
            // Extract runId from acknowledgment
            val runId = ackPayload.get("runId")?.asString
            Log.d(TAG, "Got acknowledgment with runId=$runId, waiting for response...")
            
            if (runId == null) {
                return Result.failure(IllegalStateException("No runId in acknowledgment"))
            }
            
            // Now wait for the chat response matching this runId
            withTimeout(120_000) {
                val response = chatResponses.first { response ->
                    Log.d(TAG, "Checking response: runId=${response.runId} vs $runId, complete=${response.isComplete}")
                    response.runId == runId && response.isComplete
                }
                
                if (response.error != null) {
                    Result.failure<String>(RuntimeException(response.error))
                } else {
                    Log.d(TAG, "Received response for runId=$runId: ${response.content.take(100)}...")
                    Result.success(response.content)
                }
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Log.e(TAG, "sendChatMessage failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get chat history
     */
    suspend fun getChatHistory(sk: String = sessionKey, limit: Int = 50): Result<List<Map<String, Any>>> {
        if (!isConnected.get()) {
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
        if (!isConnected.get()) {
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
        synchronized(webSocketLock) {
            webSocket?.close(1000, "User disconnect")
            webSocket = null
        }
        isConnected.set(false)
        reconnectAttempt.set(0)
        _connectionState.value = ConnectionState.DISCONNECTED
        pendingRequests.clear()
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        
        val currentAttempt = reconnectAttempt.getAndIncrement()
        if (currentAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS)")
            _connectionState.value = ConnectionState.ERROR
            return
        }
        
        // Exponential backoff with jitter
        val delay = min(
            MAX_RECONNECT_DELAY_MS,
            BASE_RECONNECT_DELAY_MS * (1L shl currentAttempt)
        ) + Random.nextLong(500)
        
        Log.d(TAG, "Scheduling reconnect attempt ${currentAttempt + 1}/$MAX_RECONNECT_ATTEMPTS in ${delay}ms")
        
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            delay(delay)
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
    
    /**
     * Parse WebSocket close code and return (userMessage, isAuthError, isRecoverable)
     * 
     * Standard close codes:
     * - 1000: Normal closure
     * - 1001: Going away (server shutting down)
     * - 1002: Protocol error
     * - 1003: Unsupported data
     * - 1006: Abnormal closure (connection lost)
     * - 1007: Invalid frame payload
     * - 1008: Policy violation (often used for auth failures)
     * - 1009: Message too big
     * - 1010: Missing extension
     * - 1011: Internal server error
     * - 1012: Service restart
     * - 1013: Try again later
     * - 1014: Bad gateway
     * - 1015: TLS handshake failure
     * - 3000-3999: Reserved for libraries/frameworks
     * - 4000-4999: Reserved for applications (OpenClaw uses these)
     */
    private fun parseCloseCode(code: Int, reason: String): Triple<String, Boolean, Boolean> {
        // Check reason string for auth hints
        val reasonLower = reason.lowercase()
        val reasonHintsAuth = reasonLower.contains("auth") || 
                              reasonLower.contains("token") || 
                              reasonLower.contains("unauthorized") ||
                              reasonLower.contains("forbidden") ||
                              reasonLower.contains("invalid")
        
        return when (code) {
            1000 -> Triple("Verbinding gesloten", false, true)
            1001 -> Triple("Server wordt herstart", false, true)
            1002 -> Triple("Protocol fout", false, false)
            1003 -> Triple("Niet-ondersteund data type", false, false)
            1006 -> Triple("Verbinding verloren", false, true)
            1007 -> Triple("Ongeldige data ontvangen", false, false)
            1008 -> {
                // Policy violation - usually auth related
                val msg = if (reasonHintsAuth || reason.isEmpty()) {
                    "Token onjuist of geen toegang"
                } else {
                    "Toegang geweigerd: $reason"
                }
                Triple(msg, true, false)
            }
            1009 -> Triple("Bericht te groot", false, false)
            1010 -> Triple("Server configuratie fout", false, false)
            1011 -> Triple("Server fout", false, true)
            1012 -> Triple("Server herstart", false, true)
            1013 -> Triple("Server overbelast, probeer later", false, true)
            1014 -> Triple("Gateway fout", false, true)
            1015 -> Triple("TLS/SSL fout", false, false)
            
            // OpenClaw custom codes (4000-4999 range)
            4001 -> Triple("Token onjuist", true, false)
            4002 -> Triple("Token verlopen", true, false)
            4003 -> Triple("Geen toegang", true, false)
            in 4000..4099 -> {
                // Assume 4000-4099 are auth-related
                val msg = reason.ifEmpty { "Authenticatie fout" }
                Triple(msg, true, false)
            }
            in 4100..4999 -> {
                // Other application errors
                val msg = reason.ifEmpty { "Applicatie fout ($code)" }
                Triple(msg, false, false)
            }
            
            else -> {
                // Unknown code - check if reason hints at auth
                if (reasonHintsAuth) {
                    Triple("Token onjuist: $reason", true, false)
                } else {
                    val msg = reason.ifEmpty { "Onbekende fout ($code)" }
                    Triple(msg, false, code in listOf(1001, 1006, 1011, 1012, 1013))
                }
            }
        }
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
