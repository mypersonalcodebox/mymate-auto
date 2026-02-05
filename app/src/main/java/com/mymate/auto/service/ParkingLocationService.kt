package com.mymate.auto.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.mymate.auto.MainActivity
import com.mymate.auto.R
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ParkingLocation
import kotlinx.coroutines.*
import java.util.*

/**
 * Service that saves parking location when Android Auto disconnects
 */
class ParkingLocationService : Service() {
    
    companion object {
        private const val TAG = "ParkingLocationService"
        private const val CHANNEL_ID = "parking_location_channel"
        private const val NOTIFICATION_ID = 2001
        private const val MIN_ACCURACY_METERS = 50f // Only save if accuracy is good
        
        fun start(context: Context) {
            // Check location permission before starting (required for Android 14+)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot start ParkingLocationService - no location permission")
                return
            }
            
            val intent = Intent(context, ParkingLocationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ParkingLocationService: ${e.message}", e)
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: AppDatabase
    private lateinit var preferencesManager: PreferencesManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ParkingLocationService created")
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            database = AppDatabase.getInstance(this)
            preferencesManager = PreferencesManager(this)
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ParkingLocationService: ${e.message}", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ParkingLocationService started - saving parking location")
        
        // Check permissions FIRST before starting foreground service (Android 14+ requirement)
        if (!hasLocationPermission()) {
            Log.e(TAG, "No location permission - cannot start foreground service with location type")
            showNotification("Geen locatie permissie", "Geef locatie toegang in instellingen")
            stopSelf()
            return START_NOT_STICKY
        }
        
        try {
            // Start as foreground service
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📍 Parkeerlocatie opslaan...")
                .setContentText("Even geduld")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground: ${e.message}", e)
            showNotification("Locatie service fout", "Herstart de app en geef permissies")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Get location and save
        saveCurrentLocation()
        
        return START_NOT_STICKY
    }
    
    @SuppressLint("MissingPermission")
    private fun saveCurrentLocation() {
        try {
            if (!hasLocationPermission()) {
                Log.w(TAG, "No location permission")
                showNotification("Geen locatie permissie", "Kan parkeerlocatie niet opslaan")
                stopSelf()
                return
            }
            
            if (!::fusedLocationClient.isInitialized) {
                Log.e(TAG, "FusedLocationClient not initialized")
                showNotification("Locatie service fout", "Herstart de app")
                stopSelf()
                return
            }
            
            // Request high accuracy location
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdates(3) // Try up to 3 times
                .build()
            
            var locationProcessed = false
            
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (locationProcessed) return
                    
                    val location = result.lastLocation
                    if (location != null && location.accuracy <= MIN_ACCURACY_METERS) {
                        locationProcessed = true
                        Log.d(TAG, "Got accurate location: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")
                        try {
                            fusedLocationClient.removeLocationUpdates(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing location updates: ${e.message}")
                        }
                        processLocation(location)
                    } else if (location != null) {
                        Log.d(TAG, "Location not accurate enough: ${location.accuracy}m (need <${MIN_ACCURACY_METERS}m)")
                    }
                }
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            // Timeout after 15 seconds
            scope.launch {
                delay(15000)
                
                if (locationProcessed) return@launch
                
                try {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing location callback: ${e.message}")
                }
                
                // Try last known location as fallback
                try {
                    val lastLocation = fusedLocationClient.lastLocation.await()
                    if (lastLocation != null && !locationProcessed) {
                        locationProcessed = true
                        Log.d(TAG, "Using last known location as fallback")
                        processLocation(lastLocation)
                    } else if (!locationProcessed) {
                        showNotification("Locatie niet gevonden", "Kon geen GPS signaal krijgen")
                        stopSelf()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting last location: ${e.message}", e)
                    if (!locationProcessed) {
                        showNotification("Locatie fout", e.message ?: "Onbekende fout")
                        stopSelf()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveCurrentLocation: ${e.message}", e)
            showNotification("Opslaan mislukt", e.message ?: "Onbekende fout")
            stopSelf()
        }
    }
    
    private fun processLocation(location: Location) {
        scope.launch {
            try {
                // Get address from coordinates
                val address = getAddressFromLocation(location.latitude, location.longitude)
                
                // Deactivate previous parking locations
                database.parkingDao().deactivateAll()
                
                // Save new parking location
                val parkingLocation = ParkingLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    address = address,
                    isActive = true
                )
                
                val id = database.parkingDao().insert(parkingLocation)
                Log.d(TAG, "Parking location saved with id: $id")
                
                // Show notification with location
                val displayText = address ?: "📍 ${location.latitude}, ${location.longitude}"
                showParkingNotification(displayText, parkingLocation.getMapsUrl())
                
                // Send to Telegram if enabled
                if (preferencesManager.getSendParkingToTelegramSync()) {
                    sendToTelegram(parkingLocation.copy(id = id))
                }
                
                // Speak location if TTS enabled
                if (preferencesManager.getTtsEnabledSync()) {
                    speakLocation(address)
                }
                
                delay(2000)
                stopSelf()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving parking location: ${e.message}", e)
                showNotification("Opslaan mislukt", e.message ?: "Onbekende fout")
                stopSelf()
            }
        }
    }
    
    private fun getAddressFromLocation(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                buildString {
                    addr.thoroughfare?.let { append(it) }
                    addr.subThoroughfare?.let { append(" $it") }
                    addr.locality?.let { 
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed: ${e.message}")
            null
        }
    }
    
    private suspend fun sendToTelegram(location: ParkingLocation) {
        try {
            // This would integrate with OpenClaw to send the location
            // For now, we'll mark it as sent
            // TODO: Implement actual Telegram sending via OpenClaw webhook
            Log.d(TAG, "Would send to Telegram: ${location.getDisplayString()}")
            database.parkingDao().markSentToTelegram(location.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to Telegram: ${e.message}")
        }
    }
    
    private fun speakLocation(address: String?) {
        try {
            val tts = TtsManager.getInstance(this)
            val text = if (address != null) {
                "Parkeerlocatie opgeslagen: $address"
            } else {
                "Parkeerlocatie opgeslagen"
            }
            tts.speak(text)
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed: ${e.message}")
        }
    }
    
    private fun showParkingNotification(locationText: String, mapsUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(mapsUrl)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Geparkeerd!")
            .setContentText(locationText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Open in Maps",
                pendingIntent
            )
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Parkeerlocatie",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaties voor parkeerlocaties"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    // Extension to await location - use Google Tasks API
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun com.google.android.gms.tasks.Task<Location>.await(): Location? {
        return try {
            com.google.android.gms.tasks.Tasks.await(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error awaiting location task: ${e.message}")
            null
        }
    }
}
