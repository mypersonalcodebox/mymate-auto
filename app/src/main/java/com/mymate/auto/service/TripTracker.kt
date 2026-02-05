package com.mymate.auto.service

import android.content.Context
import android.util.Log
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Trip
import kotlinx.coroutines.*

/**
 * Tracks driving trips based on Android Auto connection state.
 * Start trip when Android Auto connects, end when it disconnects.
 */
class TripTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "TripTracker"
        
        @Volatile
        private var INSTANCE: TripTracker? = null
        
        fun getInstance(context: Context): TripTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TripTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val db = AppDatabase.getInstance(context)
    private val tripDao = db.tripDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var currentTripId: Long? = null
    private var tripStartTime: Long? = null
    
    /**
     * Called when Android Auto connects - starts a new trip
     */
    fun startTrip() {
        scope.launch {
            try {
                // End any existing active trip first
                tripDao.getActiveTrip()?.let { activeTrip ->
                    endTripInternal(activeTrip)
                }
                
                // Start new trip
                val startTime = System.currentTimeMillis()
                val trip = Trip(
                    startTime = startTime,
                    isActive = true
                )
                
                currentTripId = tripDao.insertTrip(trip)
                tripStartTime = startTime
                
                Log.d(TAG, "Trip started: id=$currentTripId at $startTime")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting trip", e)
            }
        }
    }
    
    /**
     * Called when Android Auto disconnects - ends the current trip
     */
    fun endTrip() {
        scope.launch {
            try {
                tripDao.getActiveTrip()?.let { activeTrip ->
                    endTripInternal(activeTrip)
                }
                currentTripId = null
                tripStartTime = null
            } catch (e: Exception) {
                Log.e(TAG, "Error ending trip", e)
            }
        }
    }
    
    private suspend fun endTripInternal(trip: Trip) {
        val endTime = System.currentTimeMillis()
        val durationMinutes = ((endTime - trip.startTime) / 60000).toInt()
        
        val updatedTrip = trip.copy(
            endTime = endTime,
            durationMinutes = durationMinutes,
            isActive = false
        )
        
        tripDao.updateTrip(updatedTrip)
        Log.d(TAG, "Trip ended: id=${trip.id}, duration=${durationMinutes}min")
    }
    
    /**
     * Get current trip duration in minutes (or null if no active trip)
     */
    suspend fun getCurrentTripDuration(): Int? {
        val startTime = tripStartTime ?: return null
        return ((System.currentTimeMillis() - startTime) / 60000).toInt()
    }
    
    /**
     * Check if a trip is currently active
     */
    fun isTracking(): Boolean = currentTripId != null
    
    /**
     * Get the current trip start time
     */
    fun getTripStartTime(): Long? = tripStartTime
    
    fun cleanup() {
        scope.cancel()
    }
}
