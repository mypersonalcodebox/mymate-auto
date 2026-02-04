package com.mymate.auto.data.local

import androidx.room.*
import com.mymate.auto.data.model.Memory
import com.mymate.auto.data.model.MemoryCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<Memory>>
    
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesSync(): List<Memory>
    
    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    fun getByCategory(category: MemoryCategory): Flow<List<Memory>>
    
    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getMemoriesByCategory(category: MemoryCategory): List<Memory>
    
    @Query("SELECT DISTINCT category FROM memories")
    suspend fun getAllCategories(): List<MemoryCategory>
    
    @Query("SELECT * FROM memories WHERE category = 'TODO' ORDER BY createdAt DESC")
    fun getTodos(): Flow<List<Memory>>
    
    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<Memory>>
    
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: Long): Memory?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory): Long
    
    @Update
    suspend fun update(memory: Memory)
    
    @Delete
    suspend fun delete(memory: Memory)
    
    @Delete
    suspend fun deleteMemory(memory: Memory)
    
    @Query("UPDATE memories SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
    
    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM memories WHERE category = 'TODO'")
    suspend fun getTodoCount(): Int
}
