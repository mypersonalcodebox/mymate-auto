package com.mymate.auto.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mymate.auto.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Network utility functions for error handling and connectivity checks.
 */
object NetworkUtils {
    
    /**
     * Sealed class representing different types of network/API errors.
     */
    sealed class NetworkError {
        object NoInternet : NetworkError()
        object AuthFailed : NetworkError()
        object GatewayUnreachable : NetworkError()
        object Timeout : NetworkError()
        data class HttpError(val code: Int, val message: String) : NetworkError()
        data class Unknown(val message: String) : NetworkError()
    }
    
    /**
     * Check if the device has an active internet connection.
     */
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Parse an exception into a NetworkError type.
     */
    fun parseError(throwable: Throwable): NetworkError {
        return when (throwable) {
            is UnknownHostException -> NetworkError.GatewayUnreachable
            is SocketTimeoutException -> NetworkError.Timeout
            is IOException -> {
                val message = throwable.message ?: ""
                when {
                    message.contains("401") -> NetworkError.AuthFailed
                    message.contains("403") -> NetworkError.AuthFailed
                    message.contains("HTTP 401") -> NetworkError.AuthFailed
                    message.contains("HTTP 403") -> NetworkError.AuthFailed
                    message.contains("timeout", ignoreCase = true) -> NetworkError.Timeout
                    message.contains("Unable to resolve host") -> NetworkError.GatewayUnreachable
                    message.contains("Connection refused") -> NetworkError.GatewayUnreachable
                    message.contains("Network is unreachable") -> NetworkError.NoInternet
                    else -> NetworkError.Unknown(message)
                }
            }
            is AuthenticationException -> NetworkError.AuthFailed
            is GatewayUnreachableException -> NetworkError.GatewayUnreachable
            is TimeoutException -> NetworkError.Timeout
            else -> NetworkError.Unknown(throwable.message ?: "Onbekende fout")
        }
    }
    
    /**
     * Parse an HTTP response code into a NetworkError type.
     */
    fun parseHttpError(code: Int, message: String): NetworkError {
        return when (code) {
            401, 403 -> NetworkError.AuthFailed
            408 -> NetworkError.Timeout
            502, 503, 504 -> NetworkError.GatewayUnreachable
            in 400..499 -> NetworkError.HttpError(code, message)
            in 500..599 -> NetworkError.GatewayUnreachable
            else -> NetworkError.HttpError(code, message)
        }
    }
    
    /**
     * Get a user-friendly error message for a NetworkError.
     * Returns the string resource ID.
     */
    fun getErrorMessageResId(error: NetworkError): Int {
        return when (error) {
            is NetworkError.NoInternet -> R.string.error_no_internet
            is NetworkError.AuthFailed -> R.string.error_auth_failed
            is NetworkError.GatewayUnreachable -> R.string.error_gateway_unreachable
            is NetworkError.Timeout -> R.string.error_timeout
            is NetworkError.HttpError -> R.string.error_gateway_unreachable
            is NetworkError.Unknown -> R.string.error_unknown
        }
    }
    
    /**
     * Get a user-friendly error message string directly.
     */
    fun getErrorMessage(context: Context, error: NetworkError): String {
        return context.getString(getErrorMessageResId(error))
    }
    
    /**
     * Get a user-friendly error message from an exception.
     */
    fun getErrorMessage(context: Context, throwable: Throwable): String {
        val error = parseError(throwable)
        return getErrorMessage(context, error)
    }
    
    /**
     * Check if the error is retryable (transient).
     */
    fun isRetryable(error: NetworkError): Boolean {
        return when (error) {
            is NetworkError.NoInternet -> true
            is NetworkError.GatewayUnreachable -> true
            is NetworkError.Timeout -> true
            is NetworkError.AuthFailed -> false  // Auth errors won't fix themselves
            is NetworkError.HttpError -> error.code >= 500  // Server errors are retryable
            is NetworkError.Unknown -> true
        }
    }
}

/**
 * Custom exceptions for specific error types.
 */
class AuthenticationException(message: String = "Authenticatie mislukt") : IOException(message)
class GatewayUnreachableException(message: String = "Gateway niet bereikbaar") : IOException(message)
class TimeoutException(message: String = "Verzoek timeout") : IOException(message)
