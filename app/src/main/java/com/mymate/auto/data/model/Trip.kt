package com.mymate.auto.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a driving trip tracked via Android Auto connection
 */
@Entity(
    tableName = "trips",
    indices = [Index("startTime"), Index("endTime")]
)
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMinutes: Int? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val isActive: Boolean = true
) {
    fun getDurationText(): String {
        val minutes = durationMinutes ?: return "Actief"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 -> "${hours}u ${mins}m"
            else -> "${mins} min"
        }
    }
}
