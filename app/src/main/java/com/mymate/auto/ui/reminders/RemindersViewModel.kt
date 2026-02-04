package com.mymate.auto.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Reminder
import com.mymate.auto.data.model.RepeatType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val reminderDao = database.reminderDao()
    
    val activeReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()
    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()
    
    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()
    
    /**
     * Add a new reminder
     */
    fun addReminder(
        title: String,
        description: String? = null,
        triggerTime: Long,
        repeatType: RepeatType = RepeatType.NONE
    ) {
        if (title.isBlank()) return
        
        viewModelScope.launch {
            val reminder = Reminder(
                title = title,
                description = description,
                triggerTime = triggerTime,
                repeatType = repeatType
            )
            reminderDao.insert(reminder)
            _uiState.update { it.copy(message = "Herinnering toegevoegd!") }
            
            // TODO: Sync with OpenClaw cron for server-side reminders
        }
    }
    
    /**
     * Add a quick reminder (in X minutes)
     */
    fun addQuickReminder(title: String, minutesFromNow: Int) {
        val triggerTime = System.currentTimeMillis() + (minutesFromNow * 60 * 1000L)
        addReminder(title, null, triggerTime)
    }
    
    /**
     * Mark reminder as completed
     */
    fun completeReminder(reminder: Reminder) {
        viewModelScope.launch {
            if (reminder.repeatType == RepeatType.NONE) {
                reminderDao.markCompleted(reminder.id)
            } else {
                // For repeating reminders, schedule next occurrence
                val nextTrigger = calculateNextTrigger(reminder.triggerTime, reminder.repeatType)
                reminderDao.update(reminder.copy(triggerTime = nextTrigger))
            }
            _uiState.update { it.copy(message = "✓ Afgerond") }
        }
    }
    
    /**
     * Delete a reminder
     */
    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.delete(reminder)
            _uiState.update { it.copy(message = "Verwijderd") }
        }
    }
    
    /**
     * Update reminder
     */
    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.update(reminder)
        }
    }
    
    /**
     * Calculate next trigger time for repeating reminders
     */
    private fun calculateNextTrigger(currentTrigger: Long, repeatType: RepeatType): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTrigger }
        
        when (repeatType) {
            RepeatType.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            RepeatType.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatType.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            RepeatType.NONE -> { /* No change */ }
        }
        
        return calendar.timeInMillis
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}

data class RemindersUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
