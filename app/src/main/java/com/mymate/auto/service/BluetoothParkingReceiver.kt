package com.mymate.auto.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ParkingLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * BroadcastReceiver that auto-saves parking location when Bluetooth disconnects.
 * Useful for automatically saving where you parked when leaving your car.
 */
class BluetoothParkingReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BluetoothParkingRx"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            return
        }
        
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        Log.d(TAG, "Bluetooth disconnected: ${device?.name ?: "Unknown"}")
        
        // Check if auto-save parking is enabled
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val prefs = PreferencesManager(context)
                val autoSaveEnabled = prefs.autoSaveParking.first()
                
                if (!autoSaveEnabled) {
                    Log.d(TAG, "Auto-save parking is disabled, skipping")
                    return@launch
                }
                
                // Check location permission
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "No location permission, cannot save parking")
                    return@launch
                }
                
                // Get current location
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val cancellationToken = CancellationTokenSource()
                
                val location = try {
                    kotlinx.coroutines.tasks.await(
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationToken.token
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get location", e)
                    null
                }
                
                if (location == null) {
                    Log.w(TAG, "Could not get location")
                    return@launch
                }
                
                // Get address via geocoding
                val address = try {
                    val geocoder = Geocoder(context, Locale("nl", "NL"))
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
                
                // Save to database
                val db = AppDatabase.getInstance(context)
                val parkingLocation = ParkingLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    address = address,
                    note = "Auto-opgeslagen bij Bluetooth disconnect",
                    timestamp = System.currentTimeMillis()
                )
                
                db.parkingDao().insertParkingLocation(parkingLocation)
                Log.d(TAG, "Parking location auto-saved: $address")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in Bluetooth parking receiver", e)
            }
        }
    }
}
