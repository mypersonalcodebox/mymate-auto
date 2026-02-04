package com.mymate.auto.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a saved memory/note
 * Can be things like "Remember my parking spot is B42" or "My favorite coffee order is..."
 */
@Entity(
    tableName = "memories",
    indices = [
        Index("category"),
        Index("updatedAt")
    ]
)
data class Memory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val category: MemoryCategory = MemoryCategory.GENERAL,
    val tags: String? = null, // Comma-separated tags for search
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false, // Synced with OpenClaw memory
    val source: String = "app" // "app" or "voice" or "telegram"
)

enum class MemoryCategory {
    GENERAL,    // General notes
    PERSONAL,   // Personal preferences
    WORK,       // Work-related
    LOCATION,   // Places, addresses
    CONTACT,    // People info
    TODO        // To-do items
}
