package com.mymate.auto.data.local

import androidx.room.*
import com.mymate.auto.data.model.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<Trip>>
    
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    suspend fun getAllTripsSync(): List<Trip>
    
    @Query("SELECT * FROM trips ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentTrips(limit: Int): List<Trip>
    
    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTrip(): Trip?
    
    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    fun getActiveTripFlow(): Flow<Trip?>
    
    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Long): Trip?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long
    
    @Update
    suspend fun updateTrip(trip: Trip)
    
    @Delete
    suspend fun deleteTrip(trip: Trip)
    
    @Query("DELETE FROM trips")
    suspend fun clearAllTrips()
    
    @Query("UPDATE trips SET isActive = 0, endTime = :endTime, durationMinutes = :durationMinutes WHERE isActive = 1")
    suspend fun endActiveTrip(endTime: Long, durationMinutes: Int)
    
    @Query("SELECT SUM(durationMinutes) FROM trips WHERE durationMinutes IS NOT NULL")
    suspend fun getTotalDrivingMinutes(): Int?
    
    @Query("SELECT COUNT(*) FROM trips WHERE endTime IS NOT NULL")
    suspend fun getCompletedTripCount(): Int
}
