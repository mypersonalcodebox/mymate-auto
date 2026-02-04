package com.mymate.auto.data.local

import androidx.room.*
import com.mymate.auto.data.model.ParkingLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingDao {
    
    @Query("SELECT * FROM parking_locations ORDER BY timestamp DESC")
    fun getAllLocations(): Flow<List<ParkingLocation>>
    
    @Query("SELECT * FROM parking_locations ORDER BY timestamp DESC")
    suspend fun getAllParkingLocations(): List<ParkingLocation>
    
    @Query("SELECT * FROM parking_locations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(limit: Int = 20): Flow<List<ParkingLocation>>
    
    @Query("SELECT * FROM parking_locations WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getActiveLocation(): Flow<ParkingLocation?>
    
    @Query("SELECT * FROM parking_locations WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveLocationSync(): ParkingLocation?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: ParkingLocation): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParkingLocation(location: ParkingLocation): Long
    
    @Update
    suspend fun update(location: ParkingLocation)
    
    @Delete
    suspend fun delete(location: ParkingLocation)
    
    @Delete
    suspend fun deleteParkingLocation(location: ParkingLocation)
    
    @Query("UPDATE parking_locations SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()
    
    @Query("UPDATE parking_locations SET sentToTelegram = 1 WHERE id = :id")
    suspend fun markSentToTelegram(id: Long)
    
    @Query("DELETE FROM parking_locations WHERE timestamp < :olderThan AND isActive = 0")
    suspend fun deleteOldLocations(olderThan: Long)
    
    @Query("SELECT COUNT(*) FROM parking_locations")
    suspend fun getCount(): Int
}
