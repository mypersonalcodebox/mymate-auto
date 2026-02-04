package com.mymate.auto.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a reminder set by the user
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val triggerTime: Long, // When to remind
    val createdAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val isSynced: Boolean = false, // Synced with OpenClaw cron
    val cronJobId: String? = null, // OpenClaw cron job ID if synced
    val repeatType: RepeatType = RepeatType.NONE
)

enum class RepeatType {
    NONE,       // One-time reminder
    DAILY,      // Every day
    WEEKLY,     // Every week
    MONTHLY     // Every month
}
