package com.mymate.auto.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    IDLE,
    TESTING,
    SUCCESS,
    ERROR
}

data class SetupUiState(
    val gatewayUrl: String = "",
    val authToken: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val errorMessage: String? = null
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    init {
        // Load existing values if any
        viewModelScope.launch {
            val url = preferencesManager.getGatewayUrlSync()
            val token = preferencesManager.getGatewayTokenSync()
            _uiState.update { 
                it.copy(
                    gatewayUrl = if (url != PreferencesManager.DEFAULT_GATEWAY_URL) url else "",
                    authToken = if (token != PreferencesManager.DEFAULT_GATEWAY_TOKEN) token else ""
                )
            }
        }
    }
    
    fun updateGatewayUrl(url: String) {
        _uiState.update { 
            it.copy(
                gatewayUrl = url,
                connectionStatus = ConnectionStatus.IDLE,
                errorMessage = null
            )
        }
    }
    
    fun updateAuthToken(token: String) {
        _uiState.update { 
            it.copy(
                authToken = token,
                connectionStatus = ConnectionStatus.IDLE,
                errorMessage = null
            )
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.TESTING, errorMessage = null) }
            
            try {
                val result = withContext(Dispatchers.IO) {
                    testGatewayConnection(
                        _uiState.value.gatewayUrl,
                        _uiState.value.authToken
                    )
                }
                
                if (result.success) {
                    _uiState.update { it.copy(connectionStatus = ConnectionStatus.SUCCESS) }
                } else {
                    _uiState.update { 
                        it.copy(
                            connectionStatus = ConnectionStatus.ERROR,
                            errorMessage = result.errorMessage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        errorMessage = e.message ?: "Onbekende fout"
                    )
                }
            }
        }
    }
    
    private fun testGatewayConnection(wsUrl: String, token: String): TestResult {
        // Convert ws:// to http:// for health check
        val httpUrl = wsUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .trimEnd('/')
        
        val healthUrl = "$httpUrl/health"
        
        return try {
            val requestBuilder = Request.Builder()
                .url(healthUrl)
                .get()
            
            // Add auth header if token is provided
            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            if (response.isSuccessful) {
                TestResult(success = true)
            } else {
                TestResult(
                    success = false, 
                    errorMessage = "Server antwoordde met code ${response.code}"
                )
            }
        } catch (e: java.net.UnknownHostException) {
            TestResult(success = false, errorMessage = "Server niet gevonden")
        } catch (e: java.net.ConnectException) {
            TestResult(success = false, errorMessage = "Kan niet verbinden met server")
        } catch (e: java.net.SocketTimeoutException) {
            TestResult(success = false, errorMessage = "Verbinding timeout")
        } catch (e: Exception) {
            TestResult(success = false, errorMessage = e.message ?: "Verbindingsfout")
        }
    }
    
    fun saveConfiguration() {
        viewModelScope.launch {
            saveConfigurationInternal()
        }
    }
    
    suspend fun saveConfigurationAndWait() {
        saveConfigurationInternal()
    }
    
    private suspend fun saveConfigurationInternal() {
        preferencesManager.setGatewayUrl(_uiState.value.gatewayUrl)
        preferencesManager.setGatewayToken(_uiState.value.authToken)
        preferencesManager.setIsConfigured(true)
        
        // Also update webhook URL to match
        val webhookUrl = _uiState.value.gatewayUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .trimEnd('/') + "/hooks/agent"
        preferencesManager.setWebhookUrl(webhookUrl)
    }
    
    private data class TestResult(
        val success: Boolean,
        val errorMessage: String? = null
    )
}
