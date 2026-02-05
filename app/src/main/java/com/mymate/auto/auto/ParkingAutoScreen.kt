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

class ParkingAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "ParkingAutoScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val db = AppDatabase.getInstance(carContext)
    private val parkingDao = db.parkingDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(carContext)
    private val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale("nl", "NL"))
    
    @Volatile
    private var parkingLocations: List<ParkingLocation> = emptyList()
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var isSaving = false
    
    @Volatile
    private var statusMessage: String? = null
    
    @Volatile
    private var loadError: String? = null
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
        loadParkingLocations()
    }
    
    private fun loadParkingLocations() {
        scope.launch {
            try {
                parkingLocations = parkingDao.getAllParkingLocations()
                loadError = null
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading parking locations", e)
                loadError = "Kon locaties niet laden"
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Parkeerlocaties laden...")
                .setTitle("🅿️ Parking")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        
        // Walk to car button (only show if we have a saved location)
        val latestParking = parkingLocations.firstOrNull()
        if (latestParking != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚶 Loop naar m'n auto")
                    .addText(latestParking.address?.take(35) ?: "Navigeer naar je auto")
                    .setOnClickListener {
                        navigateWalkingToLocation(latestParking)
                    }
                    .build()
            )
        }
        
        // Save current location button
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (isSaving) "⏳ Locatie opslaan..." else "📍 Huidige locatie opslaan")
                .setOnClickListener {
                    if (!isSaving) {
                        saveCurrentLocation()
                    }
                }
                .build()
        )
        
        // Status message if any
        statusMessage?.let { msg ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(msg)
                    .build()
            )
        }
        
        // Show load error if any
        loadError?.let { error ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("⚠️ $error")
                    .addText("Probeer opnieuw te openen")
                    .build()
            )
        }
        
        // List saved locations
        if (parkingLocations.isEmpty() && loadError == null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Geen opgeslagen locaties")
                    .addText("Sla je eerste parkeerlocatie op!")
                    .build()
            )
        } else {
            parkingLocations.take(6).forEach { location ->
                val timeAgo = getTimeAgo(location.timestamp)
                val address = location.address?.take(40) ?: "Onbekend adres"
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("🚗 $address")
                        .addText(timeAgo)
                        .setOnClickListener {
                            showParkingOptions(location)
                        }
                        .build()
                )
            }
        }
        
        return ListTemplate.Builder()
            .setTitle("🅿️ Parking")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun saveCurrentLocation() {
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            statusMessage = "❌ Geen locatie permissie"
            invalidate()
            return
        }
        
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
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        statusMessage = "❌ Locatie permissie ingetrokken"
                        invalidate()
                    }
                    return@launch
                }
                
                val location = Tasks.await(locationTask, 15, TimeUnit.SECONDS)
                
                if (location != null) {
                    // Get address
                    val address = try {
                        val geocoder = Geocoder(carContext, Locale("nl", "NL"))
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
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
                    
                    val parkingLocation = ParkingLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        address = address,
                        timestamp = System.currentTimeMillis(),
                        note = null
                    )
                    
                    parkingDao.insertParkingLocation(parkingLocation)
                    parkingLocations = parkingDao.getAllParkingLocations()
                    
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        statusMessage = "✅ Locatie opgeslagen!"
                        invalidate()
                    }
                    
                    // Clear status after 3 seconds
                    delay(3000)
                    withContext(Dispatchers.Main) {
                        statusMessage = null
                        invalidate()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        statusMessage = "❌ Kon locatie niet bepalen"
                        invalidate()
                    }
                }
            } catch (e: TimeoutException) {
                Log.e(TAG, "Location timeout", e)
                withContext(Dispatchers.Main) {
                    isSaving = false
                    statusMessage = "❌ Locatie timeout - probeer opnieuw"
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving location", e)
                withContext(Dispatchers.Main) {
                    isSaving = false
                    statusMessage = "❌ Opslaan mislukt - probeer opnieuw"
                    invalidate()
                }
            } finally {
                cancellationToken.cancel()
            }
        }
    }
    
    private fun showParkingOptions(location: ParkingLocation) {
        screenManager.push(ParkingDetailScreen(carContext, location, parkingDao) {
            loadParkingLocations()
        })
    }
    
    private fun navigateWalkingToLocation(location: ParkingLocation) {
        try {
            // Walking navigation to car location
            val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            // Fallback to geo URI
            try {
                val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(Mijn auto)")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                carContext.startCarApp(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Walking navigation failed", e2)
            }
        }
    }
    
    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 1 -> "Zojuist"
            minutes < 60 -> "$minutes min geleden"
            hours < 24 -> "$hours uur geleden"
            days < 7 -> "$days dagen geleden"
            else -> dateFormat.format(Date(timestamp))
        }
    }
}

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
        val address = location.address ?: "Onbekende locatie"
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
        
        // Navigate button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🧭 Navigeer")
                .setOnClickListener {
                    navigateToLocation()
                }
                .build()
        )
        
        // Delete button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🗑️ Verwijder")
                .setOnClickListener {
                    deleteLocation()
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("🚗 Auto Locatie")
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
            // Fallback to geo URI
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
                // Still pop - the data might be corrupted, let user refresh the list
                withContext(Dispatchers.Main) {
                    screenManager.pop()
                }
            }
        }
    }
}
