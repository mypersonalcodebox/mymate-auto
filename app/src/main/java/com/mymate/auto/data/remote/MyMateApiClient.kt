package com.mymate.auto.data.remote

import com.google.gson.Gson
import com.mymate.auto.data.model.ApiRequest
import com.mymate.auto.data.model.ApiResponse
import com.mymate.auto.util.AuthenticationException
import com.mymate.auto.util.GatewayUnreachableException
import com.mymate.auto.util.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MyMateApiClient {
    
    companion object {
        // Default token for OpenClaw Gateway authentication
        private const val DEFAULT_AUTH_TOKEN = ""
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    private var authToken: String = DEFAULT_AUTH_TOKEN
    
    fun setAuthToken(token: String) {
        authToken = token
    }
    
    suspend fun sendMessage(
        webhookUrl: String,
        message: String,
        quickActionId: String? = null
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ApiRequest(
                message = message,
                source = "android_auto",
                quickActionId = quickActionId
            )
            
            val json = gson.toJson(request)
            val body = json.toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Source", "MyMate-Android-Auto")
                .addHeader("Authorization", "Bearer $authToken")
                .build()
            
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(httpRequest)
                
                continuation.invokeOnCancellation {
                    call.cancel()
                }
                
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        }
                    }
                })
            }
            
            if (!response.isSuccessful) {
                val exception = when (response.code) {
                    401 -> AuthenticationException("Ongeldige of verlopen token (401)")
                    403 -> AuthenticationException("Toegang geweigerd (403)")
                    408 -> TimeoutException("Server timeout (408)")
                    502, 503, 504 -> GatewayUnreachableException("Gateway niet beschikbaar (${response.code})")
                    in 500..599 -> GatewayUnreachableException("Server fout (${response.code})")
                    else -> IOException("HTTP ${response.code}: ${response.message}")
                }
                return@withContext Result.failure(exception)
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Empty response"))
            }
            
            val apiResponse = try {
                gson.fromJson(responseBody, ApiResponse::class.java)
            } catch (e: Exception) {
                // If response isn't proper JSON, wrap it as reply
                ApiResponse(reply = responseBody, error = null)
            }
            
            Result.success(apiResponse)
            
        } catch (e: SocketTimeoutException) {
            Result.failure(TimeoutException("Verbinding timeout: ${e.message}"))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(GatewayUnreachableException("Host niet gevonden: ${e.message}"))
        } catch (e: java.net.ConnectException) {
            Result.failure(GatewayUnreachableException("Kan niet verbinden: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun checkConnection(webhookUrl: String, callback: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url(webhookUrl.replace("/auto", "/health").let { 
                if (it == webhookUrl) "$webhookUrl/health" else it 
            })
            .head()
            .addHeader("Authorization", "Bearer $authToken")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }
            
            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful || response.code == 404)
            }
        })
    }
}
