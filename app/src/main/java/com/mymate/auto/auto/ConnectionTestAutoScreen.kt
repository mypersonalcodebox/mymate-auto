package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mymate.auto.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Android Auto screen for comprehensive gateway connection testing.
 * Tests both TCP connectivity AND WebSocket authentication.
 */
class ConnectionTestAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    companion object {
        private const val TAG = "ConnectionTestAutoScreen"
        private const val TCP_TIMEOUT_MS = 5000
        private const val WS_TIMEOUT_MS = 10000
    }
    
    private val preferencesManager = PreferencesManager(carContext)
    private val gson = Gson()
    
    @Volatile
    private var testPhase = TestPhase.IDLE
    
    @Volatile
    private var tcpResult: TestResult? = null
    
    @Volatile
    private var wsResult: TestResult? = null
    
    @Volatile
    private var testComplete = false
    
    private enum class TestPhase {
        IDLE,
        TESTING_TCP,
        TESTING_WS,
        COMPLETE
    }
    
    private data class TestResult(
        val success: Boolean,
        val message: String
    )
    
    init {
        startTests()
    }
    
    private fun startTests() {
        testPhase = TestPhase.TESTING_TCP
        tcpResult = null
        wsResult = null
        testComplete = false
        
        Thread {
            val gatewayUrl = runBlocking { preferencesManager.getGatewayUrlSync() }
            val token = runBlocking { preferencesManager.getGatewayTokenSync() }
            
            // Phase 1: TCP Connection Test
            tcpResult = testTcpConnection(gatewayUrl)
            safeInvalidate()
            
            if (tcpResult?.success == true) {
                // Phase 2: WebSocket Auth Test
                testPhase = TestPhase.TESTING_WS
                safeInvalidate()
                
                wsResult = testWebSocketAuth(gatewayUrl, token)
            } else {
                // Skip WS test if TCP failed
                wsResult = TestResult(false, "Overgeslagen (TCP mislukt)")
            }
            
            testPhase = TestPhase.COMPLETE
            testComplete = true
            safeInvalidate()
        }.start()
    }
    
    private fun testTcpConnection(gatewayUrl: String): TestResult {
        return try {
            val uri = URI(gatewayUrl.replace("ws://", "http://").replace("wss://", "https://"))
            val host = uri.host ?: throw IllegalArgumentException("Geen host in URL")
            val port = if (uri.port > 0) uri.port else 18789
            
            Log.d(TAG, "Testing TCP connection to $host:$port")
            
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS)
            socket.close()
            
            TestResult(true, "$host:$port bereikbaar")
        } catch (e: Exception) {
            Log.e(TAG, "TCP test failed", e)
            TestResult(false, e.localizedMessage ?: "Verbinding mislukt")
        }
    }
    
    private fun testWebSocketAuth(gatewayUrl: String, token: String): TestResult {
        val resultRef = AtomicReference<TestResult>()
        val latch = CountDownLatch(1)
        
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(WS_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(WS_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()
            
            // Build WebSocket URL with token
            var wsUrl = gatewayUrl.replace("http://", "ws://").replace("https://", "wss://")
            if (token.isNotEmpty()) {
                val separator = if (wsUrl.contains("?")) "&" else "?"
                wsUrl = "$wsUrl${separator}token=$token"
            }
            
            val requestBuilder = Request.Builder().url(wsUrl)
            if (token.isNotEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            
            Log.d(TAG, "Testing WebSocket auth to $wsUrl")
            
            val ws = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                private var handshakeSent = false
                
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WS opened, waiting for connect.challenge")
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "WS received: ${text.take(200)}")
                    
                    try {
                        val json = JsonParser.parseString(text).asJsonObject
                        
                        // Check for connect.challenge event
                        val event = json.get("event")?.asString
                        if (event == "connect.challenge" && !handshakeSent) {
                            handshakeSent = true
                            // Send connect handshake
                            val connectRequest = buildConnectRequest(token)
                            Log.d(TAG, "Sending connect request")
                            webSocket.send(gson.toJson(connectRequest))
                            return
                        }
                        
                        // Check for connect response
                        val type = json.get("type")?.asString
                        val id = json.get("id")?.asString
                        
                        if (type == "res" && id?.startsWith("connect-") == true) {
                            val ok = json.get("ok")?.asBoolean ?: false
                            
                            if (ok) {
                                val payload = json.getAsJsonObject("payload")
                                val payloadType = payload?.get("type")?.asString
                                
                                if (payloadType == "hello-ok") {
                                    Log.d(TAG, "Auth successful!")
                                    resultRef.set(TestResult(true, "Authenticatie geslaagd"))
                                } else {
                                    resultRef.set(TestResult(true, "Verbonden (type: $payloadType)"))
                                }
                            } else {
                                val error = json.getAsJsonObject("error")
                                val errorMsg = error?.get("message")?.asString ?: "Onbekende fout"
                                Log.e(TAG, "Auth failed: $errorMsg")
                                resultRef.set(TestResult(false, "Auth mislukt: $errorMsg"))
                            }
                            
                            webSocket.close(1000, "Test complete")
                            latch.countDown()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing WS message", e)
                    }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WS failed", t)
                    val statusCode = response?.code
                    val message = when {
                        statusCode == 401 || statusCode == 403 -> "Token ongeldig (${statusCode})"
                        t.message?.contains("401") == true -> "Token ongeldig"
                        else -> t.localizedMessage ?: "WebSocket fout"
                    }
                    resultRef.set(TestResult(false, message))
                    latch.countDown()
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WS closed: $code $reason")
                    if (resultRef.get() == null) {
                        resultRef.set(TestResult(false, "Verbinding gesloten ($code)"))
                    }
                    latch.countDown()
                }
            })
            
            // Wait for result with timeout
            if (!latch.await(WS_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)) {
                ws.cancel()
                return TestResult(false, "Timeout - geen antwoord")
            }
            
            client.dispatcher.executorService.shutdown()
            
            return resultRef.get() ?: TestResult(false, "Geen resultaat")
            
        } catch (e: Exception) {
            Log.e(TAG, "WS test error", e)
            return TestResult(false, e.localizedMessage ?: "WebSocket fout")
        }
    }
    
    private fun buildConnectRequest(token: String): JsonObject {
        val request = JsonObject()
        request.addProperty("type", "req")
        request.addProperty("id", "connect-test")
        request.addProperty("method", "connect")
        
        val params = JsonObject()
        params.addProperty("minProtocol", 3)
        params.addProperty("maxProtocol", 3)
        
        val client = JsonObject()
        client.addProperty("id", "openclaw-android-test")
        client.addProperty("displayName", "MyMate Connection Test")
        client.addProperty("version", "1.0")
        client.addProperty("platform", "android")
        client.addProperty("mode", "ui")
        params.add("client", client)
        
        params.addProperty("role", "operator")
        
        if (token.isNotEmpty()) {
            val auth = JsonObject()
            auth.addProperty("token", token)
            params.add("auth", auth)
        }
        
        request.add("params", params)
        return request
    }
    
    private fun safeInvalidate() {
        try {
            invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Invalidate failed", e)
        }
    }
    
    private fun maskUrl(url: String): String {
        return try {
            val uri = URI(url.replace("ws://", "http://").replace("wss://", "https://"))
            val host = uri.host ?: "?"
            val port = if (uri.port > 0) uri.port else 18789
            
            // Show first 4 chars of host, mask the rest
            val maskedHost = if (host.length > 8) {
                host.take(4) + "***" + host.takeLast(4)
            } else {
                host
            }
            "$maskedHost:$port"
        } catch (e: Exception) {
            "***:18789"
        }
    }
    
    override fun onGetTemplate(): Template {
        val gatewayUrl = runBlocking { preferencesManager.getGatewayUrlSync() }
        val hasToken = runBlocking { preferencesManager.getGatewayTokenSync().isNotEmpty() }
        
        // Build status text
        val statusBuilder = StringBuilder()
        
        // Gateway info
        statusBuilder.append("Gateway: ${maskUrl(gatewayUrl)}\n")
        statusBuilder.append("Token: ${if (hasToken) "✓ Ingesteld" else "✗ Ontbreekt"}\n\n")
        
        // Test results
        when (testPhase) {
            TestPhase.IDLE -> {
                statusBuilder.append("Klaar om te testen...")
            }
            TestPhase.TESTING_TCP -> {
                statusBuilder.append("🔄 TCP verbinding testen...")
            }
            TestPhase.TESTING_WS -> {
                statusBuilder.append("TCP: ${formatResult(tcpResult)}\n")
                statusBuilder.append("🔄 WebSocket auth testen...")
            }
            TestPhase.COMPLETE -> {
                statusBuilder.append("TCP: ${formatResult(tcpResult)}\n")
                statusBuilder.append("WebSocket: ${formatResult(wsResult)}\n\n")
                
                // Overall status
                val allGood = tcpResult?.success == true && wsResult?.success == true
                if (allGood) {
                    statusBuilder.append("✅ Alles werkt!")
                } else if (tcpResult?.success != true) {
                    statusBuilder.append("❌ Gateway niet bereikbaar\nControleer netwerk & URL")
                } else if (wsResult?.success != true) {
                    statusBuilder.append("❌ Authenticatie mislukt\nControleer token in app instellingen")
                }
            }
        }
        
        val builder = MessageTemplate.Builder(statusBuilder.toString())
            .setTitle("🔌 Verbindingstest")
            .setHeaderAction(Action.BACK)
        
        if (testComplete) {
            builder.addAction(
                Action.Builder()
                    .setTitle("🔄 Opnieuw")
                    .setOnClickListener {
                        startTests()
                    }
                    .build()
            )
            
            builder.addAction(
                Action.Builder()
                    .setTitle("Terug")
                    .setOnClickListener {
                        screenManager.pop()
                    }
                    .build()
            )
        }
        
        return builder.build()
    }
    
    private fun formatResult(result: TestResult?): String {
        return when {
            result == null -> "⏳ Wachten..."
            result.success -> "✅ ${result.message}"
            else -> "❌ ${result.message}"
        }
    }
}
