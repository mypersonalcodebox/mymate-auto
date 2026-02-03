package com.mymate.auto.data.repository

import com.mymate.auto.data.local.ChatDao
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ChatMessage
import com.mymate.auto.data.model.QuickAction
import com.mymate.auto.data.model.QuickActions
import com.mymate.auto.data.remote.MyMateApiClient
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiClient: MyMateApiClient,
    private val preferencesManager: PreferencesManager
) {
    
    fun getRecentMessages(limit: Int = 100): Flow<List<ChatMessage>> {
        return chatDao.getRecentMessages(limit)
    }
    
    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatDao.getAllMessages()
    }
    
    suspend fun sendMessage(
        message: String,
        quickActionId: String? = null
    ): Result<ChatMessage> {
        // Save user message
        val userMessage = ChatMessage(
            content = message,
            isFromUser = true,
            quickActionId = quickActionId
        )
        chatDao.insertMessage(userMessage)
        
        // Track action usage if applicable
        quickActionId?.let {
            preferencesManager.incrementActionUsage(it)
        }
        
        // Send to API
        val webhookUrl = preferencesManager.getWebhookUrlSync()
        val result = apiClient.sendMessage(webhookUrl, message, quickActionId)
        
        return result.fold(
            onSuccess = { response ->
                val reply = response.reply ?: response.error ?: "Geen antwoord ontvangen"
                val botMessage = ChatMessage(
                    content = reply,
                    isFromUser = false,
                    quickActionId = quickActionId
                )
                chatDao.insertMessage(botMessage)
                Result.success(botMessage)
            },
            onFailure = { error ->
                val errorMessage = ChatMessage(
                    content = "❌ Fout: ${error.localizedMessage ?: "Onbekende fout"}",
                    isFromUser = false,
                    quickActionId = quickActionId
                )
                chatDao.insertMessage(errorMessage)
                Result.failure(error)
            }
        )
    }
    
    suspend fun clearHistory() {
        chatDao.clearAllMessages()
    }
    
    suspend fun deleteOldMessages(daysOld: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        chatDao.deleteOldMessages(cutoffTime)
    }
    
    suspend fun getSortedQuickActions(): List<QuickAction> {
        val usageMap = preferencesManager.getActionUsage()
        val lastUsedMap = preferencesManager.getActionLastUsed()
        
        return QuickActions.defaultActions.map { action ->
            action.copy(
                usageCount = usageMap[action.id] ?: 0,
                lastUsed = lastUsedMap[action.id] ?: 0
            )
        }.sortedWith(
            compareByDescending<QuickAction> { it.usageCount }
                .thenByDescending { it.lastUsed }
        )
    }
}
