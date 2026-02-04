package com.mymate.auto.data.local

import androidx.room.*
import com.mymate.auto.data.model.ConversationMessage
import kotlinx.coroutines.flow.Flow

/**
 * DAO for conversation messages - separate table from quick action chat
 */
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 100): Flow<List<ConversationMessage>>
    
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<ConversationMessage>>
    
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesList(limit: Int = 50): List<ConversationMessage>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessage): Long
    
    @Delete
    suspend fun deleteMessage(message: ConversationMessage)
    
    @Query("DELETE FROM conversation_messages")
    suspend fun clearAllMessages()
    
    @Query("DELETE FROM conversation_messages WHERE timestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM conversation_messages")
    suspend fun getMessageCount(): Int
}
