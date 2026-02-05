package com.mymate.auto.auto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.service.TripTracker
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MyMateCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return MyMateSession()
    }
}

class MyMateSession : Session() {
    private val TAG = "MyMateSession"
    
    // Use a single-threaded executor for background work
    private val executor = Executors.newSingleThreadExecutor()
    private val isDestroyed = AtomicBoolean(false)
    
    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "Android Auto session started")
        
        // Start trip tracking when Android Auto connects
        val tripTracker = TripTracker.getInstance(carContext)
        tripTracker.startTrip()
        Log.d(TAG, "Trip tracking started")
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                Log.d(TAG, "Android Auto session ending - sending parking location and ending trip")
                isDestroyed.set(true)
                
                // End trip tracking when Android Auto disconnects
                tripTracker.endTrip()
                Log.d(TAG, "Trip tracking ended")
                
                // Send parking location in background, but don't block
                executor.submit {
                    try {
                        sendParkingLocation()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending parking location", e)
                    }
                }
                
                // Shutdown executor after a delay to allow parking notification to complete
                executor.shutdown()
                try {
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Executor shutdown interrupted")
                }
            }
        })
        
        return MainAutoScreen(carContext)
    }
    
    private fun sendParkingLocation() {
        if (isDestroyed.get()) {
            Log.d(TAG, "Session destroyed, attempting to send parking location anyway")
        }
        
        try {
            val location = getLastKnownLocation()
            sendParkingNotification(location)
            
            if (location != null) {
                Log.d(TAG, "Parking location sent: ${location.latitude}, ${location.longitude}")
            } else {
                Log.w(TAG, "Parking notification sent without location")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendParkingLocation", e)
        }
    }
    
    private fun getLastKnownLocation(): Location? {
        val context = carContext
        
        // Check permissions
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w(TAG, "Location permission not granted")
            return null
        }
        
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Try GPS first, then network
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            
            // Check if location is recent (within last 10 minutes)
            if (location != null) {
                val age = System.currentTimeMillis() - location.time
                if (age > 10 * 60 * 1000) {
                    Log.w(TAG, "Location is stale: ${age / 1000}s old")
                }
            }
            
            location
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
    
    private fun sendParkingNotification(location: Location?) {
        val preferencesManager = PreferencesManager(carContext)
        val webhookUrl = try {
            runBlocking { preferencesManager.getWebhookUrlSync() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get webhook URL", e)
            return
        }
        if (webhookUrl.isEmpty()) {
            Log.d(TAG, "No webhook URL configured, skipping parking notification")
            return
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        
        val json = JSONObject().apply {
            put("action", "parking")
            put("message", "Auto geparkeerd")
            put("source", "android_auto")
            if (location != null) {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy)
                put("timestamp", location.time)
            }
        }
        
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        Log.d(TAG, "Parking notification sent successfully: ${response.code}")
                    }
                    response.code == 401 || response.code == 403 -> {
                        // Auth errors - log but don't crash, webhook may need reconfiguration
                        Log.w(TAG, "Parking notification auth failed (${response.code}): webhook may need reconfiguration")
                    }
                    response.code in 400..499 -> {
                        // Client errors - log the issue
                        Log.w(TAG, "Parking notification client error: ${response.code} ${response.message}")
                    }
                    response.code in 500..599 -> {
                        // Server errors - transient, just log
                        Log.w(TAG, "Parking notification server error: ${response.code} ${response.message}")
                    }
                    else -> {
                        Log.w(TAG, "Parking notification unexpected response: ${response.code}")
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Parking notification timed out")
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Parking notification failed: no network or unknown host")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Parking notification IO error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send parking notification", e)
        }
    }
}
