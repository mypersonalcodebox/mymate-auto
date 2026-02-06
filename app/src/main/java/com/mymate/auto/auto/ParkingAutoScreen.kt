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

/**
 * Rock-solid simple parking screen with just 3 things:
 * 1. "📍 Locatie opslaan" button - saves current GPS
 * 2. "🚶 Navigeer naar auto" button - opens Google Maps walking
 * 3. Shows last saved location info (address + time)
 */
class ParkingAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "ParkingAutoScreen"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val parkingDao = AppDatabase.getInstance(carContext).parkingDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(carContext)
    private val dateFormat = SimpleDateFormat("EEEE d MMM HH:mm", Locale("nl", "NL"))
    
    @Volatile private var lastParking: ParkingLocation? = null
    @Volatile private var isSaving = false
    @Volatile private var statusMessage: String? = null
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { job.cancel() }
        })
        loadLastParking()
    }
    
    private fun loadLastParking() {
        scope.launch {
            try {
                lastParking = parkingDao.getAllParkingLocations().firstOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "Load failed", e)
            }
            withContext(Dispatchers.Main) { invalidate() }
        }
    }
    
    override fun onGetTemplate(): Template {
        // Permission check using applicationContext (more reliable)
        val hasFine = ContextCompat.checkSelfPermission(
            carContext.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            carContext.applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasFine && !hasCoarse) {
            return MessageTemplate.Builder(
                "Open de MyMate app op je telefoon en geef locatie permissie."
            )
                .setTitle("📍 Locatie permissie nodig")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        
        // 1. Save location button
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (isSaving) "⏳ Opslaan..." else "📍 Locatie opslaan")
                .addText("Sla huidige GPS positie op")
                .setOnClickListener { if (!isSaving) saveCurrentLocation() }
                .build()
        )
        
        // 2 & 3. Last parking info + navigate button
        val parking = lastParking
        if (parking != null) {
            val address = parking.address ?: "Onbekend adres"
            val timeAgo = getTimeAgo(parking.timestamp)
            
            // Show saved location info
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚗 $address")
                    .addText("Geparkeerd $timeAgo")
                    .build()
            )
            
            // Navigate button
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚶 Navigeer naar auto")
                    .addText("Open Google Maps wandelroute")
                    .setOnClickListener { navigateToParking(parking) }
                    .build()
            )
        } else {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Nog geen locatie opgeslagen")
                    .addText("Tik hierboven om op te slaan")
                    .build()
            )
        }
        
        // Status message (success/error feedback)
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
        
        val cancelToken = CancellationTokenSource()
        scope.launch {
            try {
                val locationTask = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancelToken.token
                )
                val location = Tasks.await(locationTask, 15, TimeUnit.SECONDS)
                
                if (location != null) {
                    val address = getAddress(location.latitude, location.longitude)
                    val parking = ParkingLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        address = address,
                        timestamp = System.currentTimeMillis(),
                        note = null
                    )
                    parkingDao.insertParkingLocation(parking)
                    lastParking = parking
                    showStatus("✅ Opgeslagen!")
                } else {
                    showStatus("❌ Geen GPS signaal")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error", e)
                showStatus("❌ Geen locatie permissie")
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
                showStatus("❌ Opslaan mislukt")
            } finally {
                cancelToken.cancel()
            }
        }
    }
    
    private suspend fun showStatus(message: String) {
        withContext(Dispatchers.Main) {
            isSaving = false
            statusMessage = message
            invalidate()
        }
        delay(3000)
        withContext(Dispatchers.Main) {
            statusMessage = null
            invalidate()
        }
    }
    
    private fun getAddress(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(carContext, Locale("nl", "NL"))
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let { addr ->
                listOfNotNull(
                    addr.thoroughfare,
                    addr.subThoroughfare?.let { " $it" },
                    addr.locality?.let { ", $it" }
                ).joinToString("").ifEmpty { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocode failed", e)
            null
        }
    }
    
    private fun navigateToParking(parking: ParkingLocation) {
        try {
            // Try Google Maps walking navigation first
            val uri = Uri.parse("google.navigation:q=${parking.latitude},${parking.longitude}&mode=w")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Maps failed, trying fallback", e)
            try {
                // Fallback to generic geo intent
                val uri = Uri.parse("geo:${parking.latitude},${parking.longitude}?q=${parking.latitude},${parking.longitude}(Auto)")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                carContext.startCarApp(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Navigation failed completely", e2)
            }
        }
    }
    
    private fun getTimeAgo(timestamp: Long): String {
        val minutes = (System.currentTimeMillis() - timestamp) / 60000
        return when {
            minutes < 1 -> "zojuist"
            minutes < 60 -> "$minutes min geleden"
            minutes < 1440 -> "${minutes / 60} uur geleden"
            minutes < 10080 -> "${minutes / 1440} dagen geleden"
            else -> dateFormat.format(Date(timestamp))
        }
    }
}
