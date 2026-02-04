package com.mymate.auto.data.local

import androidx.room.*
import com.mymate.auto.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTime ASC")
    fun getActiveReminders(): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders ORDER BY triggerTime DESC")
    fun getAllReminders(): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND triggerTime <= :time ORDER BY triggerTime ASC")
    suspend fun getDueReminders(time: Long = System.currentTimeMillis()): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long
    
    @Update
    suspend fun update(reminder: Reminder)
    
    @Delete
    suspend fun delete(reminder: Reminder)
    
    @Query("UPDATE reminders SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)
    
    @Query("UPDATE reminders SET isSynced = 1, cronJobId = :cronJobId WHERE id = :id")
    suspend fun markSynced(id: Long, cronJobId: String)
    
    @Query("DELETE FROM reminders WHERE isCompleted = 1 AND triggerTime < :olderThan")
    suspend fun deleteOldCompleted(olderThan: Long)
}
