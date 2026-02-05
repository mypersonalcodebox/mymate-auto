package com.mymate.auto.util

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Crash-safe wrapper for OkHttp calls with standardized error handling.
 * All error messages are in Dutch for user-facing display.
 */
object SafeHttpClient {
    private const val TAG = "SafeHttpClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    /**
     * Perform a POST request with JSON body.
     * 
     * @param url The endpoint URL
     * @param token Bearer token for Authorization header
     * @param body JSON body as string
     * @param onSuccess Called with response body on success (2xx)
     * @param onError Called with Dutch error message on failure
     */
    fun post(
        url: String,
        token: String,
        body: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build request: ${e.message}", e)
            onError("Ongeldige URL of request configuratie")
            return
        }
        
        executeRequest(request, onSuccess, onError)
    }
    
    /**
     * Perform a GET request.
     */
    fun get(
        url: String,
        token: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build request: ${e.message}", e)
            onError("Ongeldige URL of request configuratie")
            return
        }
        
        executeRequest(request, onSuccess, onError)
    }
    
    /**
     * Perform a PUT request with JSON body.
     */
    fun put(
        url: String,
        token: String,
        body: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .put(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build request: ${e.message}", e)
            onError("Ongeldige URL of request configuratie")
            return
        }
        
        executeRequest(request, onSuccess, onError)
    }
    
    /**
     * Perform a DELETE request.
     */
    fun delete(
        url: String,
        token: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = try {
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build request: ${e.message}", e)
            onError("Ongeldige URL of request configuratie")
            return
        }
        
        executeRequest(request, onSuccess, onError)
    }
    
    /**
     * Execute request asynchronously with comprehensive error handling.
     */
    private fun executeRequest(
        request: Request,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMessage = getNetworkErrorMessage(e)
                Log.e(TAG, "Request failed: ${request.url} - ${e.message}", e)
                onError(errorMessage)
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    try {
                        val body = resp.body?.string() ?: ""
                        
                        if (resp.isSuccessful) {
                            Log.d(TAG, "Request succeeded: ${request.url} (${resp.code})")
                            onSuccess(body)
                        } else {
                            val errorMessage = getHttpErrorMessage(resp.code, body)
                            Log.w(TAG, "Request failed: ${request.url} - HTTP ${resp.code}: $body")
                            onError(errorMessage)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process response: ${e.message}", e)
                        onError("Fout bij verwerken van antwoord")
                    }
                }
            }
        })
    }
    
    /**
     * Get Dutch error message for HTTP status codes.
     */
    fun getHttpErrorMessage(code: Int, body: String? = null): String = when (code) {
        400 -> "Ongeldige aanvraag - controleer de invoer"
        401 -> "Authenticatie mislukt - controleer je token"
        403 -> "Geen toegang - je hebt geen rechten voor deze actie"
        404 -> "Niet gevonden - de gevraagde resource bestaat niet"
        408 -> "Aanvraag timeout - de server reageerde niet op tijd"
        409 -> "Conflict - de resource is al in gebruik of gewijzigd"
        422 -> "Onverwerkbare data - controleer de invoer"
        429 -> "Te veel aanvragen - wacht even en probeer opnieuw"
        500 -> "Interne server fout - probeer later opnieuw"
        502 -> "Server niet bereikbaar - probeer later opnieuw"
        503 -> "Service tijdelijk niet beschikbaar - probeer later opnieuw"
        504 -> "Gateway timeout - de server reageerde niet op tijd"
        else -> "Onbekende fout (HTTP $code)"
    }
    
    /**
     * Get Dutch error message for network/connection errors.
     */
    fun getNetworkErrorMessage(e: Exception): String = when (e) {
        is SocketTimeoutException -> "Verbinding timeout - controleer je internetverbinding"
        is UnknownHostException -> "Server niet gevonden - controleer je internetverbinding"
        is SSLException -> "Beveiligingsfout - kan geen veilige verbinding maken"
        is IOException -> {
            when {
                e.message?.contains("timeout", ignoreCase = true) == true -> 
                    "Verbinding timeout - probeer opnieuw"
                e.message?.contains("refused", ignoreCase = true) == true -> 
                    "Verbinding geweigerd - server is niet bereikbaar"
                e.message?.contains("reset", ignoreCase = true) == true -> 
                    "Verbinding onderbroken - probeer opnieuw"
                else -> "Netwerkfout - controleer je internetverbinding"
            }
        }
        else -> "Onverwachte fout - probeer opnieuw"
    }
    
    /**
     * Cancel all pending requests.
     */
    fun cancelAll() {
        client.dispatcher.cancelAll()
    }
}
