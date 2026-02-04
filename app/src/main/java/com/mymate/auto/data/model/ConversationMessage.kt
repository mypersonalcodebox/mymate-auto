package com.mymate.auto.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Message entity for conversation mode - separate table from quick action chat
 */
@Entity(tableName = "conversation_messages")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val topic: String? = null // Conversation topic if set
)
