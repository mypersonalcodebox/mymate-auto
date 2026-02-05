package com.mymate.auto.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.ParkingLocation
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Simple parking screen with 3 action buttons:
 * 1. Save current location
 * 2. Show last saved parking location
 * 3. Navigate to car (walking)
 */
class ParkingAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "ParkingAutoScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val db = AppDatabase.getInstance(carContext)
    private val parkingDao = db.parkingDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(carContext)
    private val dateFormat = SimpleDateFormat("EEEE d MMM HH:mm", Locale("nl", "NL"))
    
    @Volatile private var lastParking: ParkingLocation? = null
    @Volatile private var isLoading = true
    @Volatile private var isSaving = false
    @Volatile private var statusMessage: String? = null
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
        loadLastParking()
    }
    
    private fun loadLastParking() {
        scope.launch {
            try {
                lastParking = parkingDao.getAllParkingLocations().firstOrNull()
                isLoading = false
                withContext(Dispatchers.Main) { invalidate() }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading parking", e)
                isLoading = false
                withContext(Dispatchers.Main) { invalidate() }
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        // Check location permission first
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasLocationPermission) {
            return MessageTemplate.Builder(
                "Open de MyMate app op je telefoon en geef locatie permissie om deze functie te gebruiken."
            )
                .setTitle("📍 Locatie permissie nodig")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        if (isLoading) {
            return MessageTemplate.Builder("Even geduld...")
                .setTitle("🅿️ Parking")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        
        // Button 1: Save current location
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (isSaving) "⏳ Locatie opslaan..." else "📍 Huidige locatie opslaan")
                .addText("Sla je huidige GPS locatie op")
                .setOnClickListener {
                    if (!isSaving) saveCurrentLocation()
                }
                .build()
        )
        
        // Button 2: Show last parking location
        val parking = lastParking
        if (parking != null) {
            val address = parking.address ?: "Onbekend adres"
            val timeAgo = getTimeAgo(parking.timestamp)
            
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚗 Laatste parkeerplaats")
                    .addText("$address • $timeAgo")
                    .setOnClickListener {
                        screenManager.push(ParkingDetailScreen(carContext, parking, parkingDao) {
                            loadLastParking()
                        })
                    }
                    .build()
            )
            
            // Button 3: Navigate to car (walking)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚶 Navigeer naar auto")
                    .addText("Open Google Maps wandelroute")
                    .setOnClickListener {
                        navigateWalkingToLocation(parking)
                    }
                    .build()
            )
        } else {
            // No saved locations
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚗 Nog geen locaties opgeslagen")
                    .addText("Sla eerst je parkeerplaats op")
                    .build()
            )
        }
        
        // Status message (inline)
        statusMessage?.let { msg ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(msg)
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("🅿️ Parking")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun saveCurrentLocation() {
        isSaving = true
        statusMessage = null
        invalidate()
        
        val cancellationToken = CancellationTokenSource()
        scope.launch {
            try {
                val locationTask = try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationToken.token
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "Location permission revoked", e)
                    showStatus("❌ Locatie permissie ingetrokken", isError = true)
                    return@launch
                }
                
                val location = Tasks.await(locationTask, 15, TimeUnit.SECONDS)
                
                if (location != null) {
                    val address = getAddress(location.latitude, location.longitude)
                    
                    val parkingLocation = ParkingLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        address = address,
                        timestamp = System.currentTimeMillis(),
                        note = null
                    )
                    
                    parkingDao.insertParkingLocation(parkingLocation)
                    lastParking = parkingLocation
                    
                    showStatus("✅ Locatie opgeslagen!", isError = false)
                } else {
                    showStatus("❌ Kon locatie niet bepalen", isError = true)
                }
            } catch (e: TimeoutException) {
                Log.e(TAG, "Location timeout", e)
                showStatus("❌ GPS timeout - probeer opnieuw", isError = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving location", e)
                showStatus("❌ Opslaan mislukt", isError = true)
            } finally {
                cancellationToken.cancel()
            }
        }
    }
    
    private suspend fun showStatus(message: String, isError: Boolean) {
        withContext(Dispatchers.Main) {
            isSaving = false
            statusMessage = message
            invalidate()
        }
        
        // Clear status after delay (longer for errors)
        delay(if (isError) 4000L else 3000L)
        withContext(Dispatchers.Main) {
            statusMessage = null
            invalidate()
        }
    }
    
    private fun getAddress(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(carContext, Locale("nl", "NL"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                buildString {
                    addr.thoroughfare?.let { append(it) }
                    addr.subThoroughfare?.let { append(" $it") }
                    if (isNotEmpty()) append(", ")
                    addr.locality?.let { append(it) }
                }.ifEmpty { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            null
        }
    }
    
    private fun navigateWalkingToLocation(location: ParkingLocation) {
        try {
            val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            try {
                val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(Mijn auto)")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                carContext.startCarApp(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Navigation failed", e2)
            }
        }
    }
    
    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 1 -> "zojuist"
            minutes < 60 -> "$minutes min"
            hours < 24 -> "$hours uur"
            days < 7 -> "$days dagen"
            else -> dateFormat.format(Date(timestamp))
        }
    }
}

/**
 * Detail screen for a parking location with navigate and delete options.
 */
class ParkingDetailScreen(
    carContext: CarContext,
    private val location: ParkingLocation,
    private val parkingDao: com.mymate.auto.data.local.ParkingDao,
    private val onDeleted: () -> Unit
) : Screen(carContext) {
    
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val dateFormat = SimpleDateFormat("EEEE d MMMM HH:mm", Locale("nl", "NL"))
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
    }
    
    override fun onGetTemplate(): Template {
        val address = location.address ?: "Onbekend adres"
        val time = dateFormat.format(Date(location.timestamp))
        
        val paneBuilder = Pane.Builder()
        
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📍 $address")
                .addText("Geparkeerd: $time")
                .build()
        )
        
        location.note?.let { note ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("📝 Notitie")
                    .addText(note)
                    .build()
            )
        }
        
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📐 Nauwkeurigheid")
                .addText("${location.accuracy.toInt()} meter")
                .build()
        )
        
        // Navigate button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🚶 Navigeer")
                .setOnClickListener { navigateToLocation() }
                .build()
        )
        
        // Delete button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🗑️ Verwijder")
                .setOnClickListener { deleteLocation() }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("🚗 Parkeerlocatie")
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun navigateToLocation() {
        try {
            val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            try {
                val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                carContext.startCarApp(intent)
            } catch (e2: Exception) {
                Log.e("ParkingDetailScreen", "Navigation failed", e2)
            }
        }
    }
    
    private fun deleteLocation() {
        scope.launch {
            try {
                parkingDao.deleteParkingLocation(location)
                withContext(Dispatchers.Main) {
                    onDeleted()
                    screenManager.pop()
                }
            } catch (e: Exception) {
                Log.e("ParkingDetailScreen", "Delete failed", e)
                withContext(Dispatchers.Main) {
                    screenManager.pop()
                }
            }
        }
    }
}
