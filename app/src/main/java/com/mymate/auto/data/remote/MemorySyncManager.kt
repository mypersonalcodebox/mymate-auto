package com.mymate.auto.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Memory
import com.mymate.auto.data.model.MemoryCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles syncing memories between the app and OpenClaw gateway
 * 
 * When the AI detects something to remember, it sends a memory_sync message
 * which this manager processes and saves to the local database.
 */
class MemorySyncManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "MemorySyncManager"
        
        @Volatile
        private var instance: MemorySyncManager? = null
        
        fun getInstance(context: Context): MemorySyncManager {
            return instance ?: synchronized(this) {
                instance ?: MemorySyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = AppDatabase.getInstance(context)
    private val memoryDao = database.memoryDao()
    private val gson = Gson()
    
    /**
     * Process an incoming memory sync payload from the gateway
     */
    fun processMemorySync(payload: String) {
        scope.launch {
            try {
                val syncData = gson.fromJson(payload, MemorySyncPayload::class.java)
                
                when (syncData.action) {
                    "add" -> addMemory(syncData)
                    "update" -> updateMemory(syncData)
                    "delete" -> deleteMemory(syncData)
                    else -> Log.w(TAG, "Unknown sync action: ${syncData.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process memory sync: ${e.message}", e)
            }
        }
    }
    
    private suspend fun addMemory(data: MemorySyncPayload) {
        val category = parseCategory(data.category)
        
        val memory = Memory(
            content = data.content,
            category = category,
            tags = data.tags,
            source = data.source ?: "voice",
            isSynced = true
        )
        
        val id = memoryDao.insert(memory)
        Log.d(TAG, "Memory synced from gateway: id=$id, content=${data.content.take(50)}")
    }
    
    private suspend fun updateMemory(data: MemorySyncPayload) {
        data.id?.let { id ->
            val existing = memoryDao.getById(id)
            if (existing != null) {
                val updated = existing.copy(
                    content = data.content,
                    category = parseCategory(data.category),
                    tags = data.tags,
                    updatedAt = System.currentTimeMillis()
                )
                memoryDao.update(updated)
                Log.d(TAG, "Memory updated from gateway: id=$id")
            }
        }
    }
    
    private suspend fun deleteMemory(data: MemorySyncPayload) {
        data.id?.let { id ->
            val existing = memoryDao.getById(id)
            if (existing != null) {
                memoryDao.delete(existing)
                Log.d(TAG, "Memory deleted from gateway: id=$id")
            }
        }
    }
    
    private fun parseCategory(categoryStr: String?): MemoryCategory {
        return when (categoryStr?.lowercase()) {
            "personal", "persoonlijk" -> MemoryCategory.PERSONAL
            "work", "werk" -> MemoryCategory.WORK
            "location", "locatie" -> MemoryCategory.LOCATION
            "contact" -> MemoryCategory.CONTACT
            "todo", "to-do" -> MemoryCategory.TODO
            else -> MemoryCategory.GENERAL
        }
    }
    
    /**
     * Create a memory locally and mark for sync
     */
    suspend fun createMemory(content: String, category: MemoryCategory = MemoryCategory.GENERAL, source: String = "app"): Long {
        val memory = Memory(
            content = content,
            category = category,
            source = source,
            isSynced = false
        )
        return memoryDao.insert(memory)
    }
}

/**
 * Payload structure for memory sync messages from gateway
 */
data class MemorySyncPayload(
    @SerializedName("action")
    val action: String, // "add", "update", "delete"
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("category")
    val category: String? = null,
    
    @SerializedName("tags")
    val tags: String? = null,
    
    @SerializedName("source")
    val source: String? = null,
    
    @SerializedName("id")
    val id: Long? = null
)
