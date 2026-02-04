package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Reminder
import com.mymate.auto.data.model.RepeatType
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class RemindersAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "RemindersAutoScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val db = AppDatabase.getInstance(carContext)
    private val reminderDao = db.reminderDao()
    private val dateFormat = SimpleDateFormat("EEE d MMM HH:mm", Locale("nl", "NL"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("nl", "NL"))
    
    @Volatile
    private var reminders: List<Reminder> = emptyList()
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var showCompleted = false
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
        loadReminders()
    }
    
    private fun loadReminders() {
        scope.launch {
            try {
                reminders = if (showCompleted) {
                    reminderDao.getAllRemindersSync()
                } else {
                    reminderDao.getPendingReminders()
                }
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading reminders", e)
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Herinneringen laden...")
                .setTitle("⏰ Herinneringen")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        
        // Quick add options
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏱️ Over 5 minuten")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "reminder_5min") { text ->
                            addReminder(text, 5)
                        }
                    )
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏱️ Over 15 minuten")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "reminder_15min") { text ->
                            addReminder(text, 15)
                        }
                    )
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏱️ Over 1 uur")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "reminder_1hr") { text ->
                            addReminder(text, 60)
                        }
                    )
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Aangepaste herinnering")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "reminder_custom") { text ->
                            addReminderWithParsing(text)
                        }
                    )
                }
                .build()
        )
        
        // Toggle completed
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (showCompleted) "👁️ Verberg voltooide" else "👁️ Toon voltooide")
                .setOnClickListener {
                    showCompleted = !showCompleted
                    isLoading = true
                    invalidate()
                    loadReminders()
                }
                .build()
        )
        
        // Show reminders
        if (reminders.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Geen herinneringen")
                    .addText(if (showCompleted) "Je hebt nog geen herinneringen" else "Geen openstaande herinneringen")
                    .build()
            )
        } else {
            reminders.take(5).forEach { reminder ->
                val statusEmoji = if (reminder.isCompleted) "✅" else "⏰"
                val timeText = formatReminderTime(reminder.triggerTime)
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("$statusEmoji ${reminder.title.take(40)}")
                        .addText(timeText)
                        .setOnClickListener {
                            screenManager.push(ReminderDetailScreen(carContext, reminder, reminderDao, scope) {
                                loadReminders()
                            })
                        }
                        .build()
                )
            }
            
            if (reminders.size > 5) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("📋 ${reminders.size - 5} meer...")
                        .addText("Bekijk in de phone app")
                        .build()
                )
            }
        }
        
        return ListTemplate.Builder()
            .setTitle("⏰ Herinneringen")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun addReminder(title: String, minutesFromNow: Int) {
        scope.launch {
            try {
                val triggerTime = System.currentTimeMillis() + (minutesFromNow * 60 * 1000L)
                
                val reminder = Reminder(
                    title = title,
                    description = null,
                    triggerTime = triggerTime,
                    createdAt = System.currentTimeMillis(),
                    isCompleted = false,
                    repeatType = RepeatType.NONE
                )
                
                reminderDao.insertReminder(reminder)
                loadReminders()
                
                val timeText = when (minutesFromNow) {
                    5 -> "5 minuten"
                    15 -> "15 minuten"
                    60 -> "1 uur"
                    else -> "$minutesFromNow minuten"
                }
                
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "✅ Herinnering ingesteld!", "Je wordt over $timeText herinnerd aan:\n\n\"$title\"") {
                            screenManager.popToRoot()
                            screenManager.push(RemindersAutoScreen(carContext))
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding reminder", e)
            }
        }
    }
    
    private fun addReminderWithParsing(text: String) {
        // Try to parse time from message
        val lower = text.lowercase()
        
        val minutesFromNow = when {
            lower.contains("5 min") -> 5
            lower.contains("10 min") -> 10
            lower.contains("15 min") -> 15
            lower.contains("30 min") || lower.contains("half uur") -> 30
            lower.contains("1 uur") || lower.contains("een uur") -> 60
            lower.contains("2 uur") -> 120
            lower.contains("morgen") -> 24 * 60
            else -> 30 // Default to 30 minutes
        }
        
        // Remove time indicators from message
        val cleanTitle = text
            .replace(Regex("over \\d+ min(uten)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("over \\d+ uur", RegexOption.IGNORE_CASE), "")
            .replace("morgen", "", ignoreCase = true)
            .replace("herinner me aan", "", ignoreCase = true)
            .replace("herinner me", "", ignoreCase = true)
            .trim()
        
        addReminder(cleanTitle.ifEmpty { text }, minutesFromNow)
    }
    
    private fun formatReminderTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now
        
        return when {
            diff < 0 -> "Verlopen: ${dateFormat.format(Date(timestamp))}"
            diff < 60 * 1000 -> "Minder dan 1 minuut"
            diff < 60 * 60 * 1000 -> "Over ${diff / (60 * 1000)} minuten"
            diff < 24 * 60 * 60 * 1000 -> "Vandaag om ${timeFormat.format(Date(timestamp))}"
            diff < 48 * 60 * 60 * 1000 -> "Morgen om ${timeFormat.format(Date(timestamp))}"
            else -> dateFormat.format(Date(timestamp))
        }
    }
}

class ReminderDetailScreen(
    carContext: CarContext,
    private val reminder: Reminder,
    private val reminderDao: com.mymate.auto.data.local.ReminderDao,
    parentScope: CoroutineScope,
    private val onUpdated: () -> Unit
) : Screen(carContext) {
    
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val dateFormat = SimpleDateFormat("EEEE d MMMM HH:mm", Locale("nl", "NL"))
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
    }
    
    override fun onGetTemplate(): Template {
        val triggerTime = dateFormat.format(Date(reminder.triggerTime))
        val status = if (reminder.isCompleted) "Voltooid ✅" else "Actief ⏰"
        
        val paneBuilder = Pane.Builder()
        
        // Title
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📝 Herinnering")
                .addText(reminder.title)
                .build()
        )
        
        // Description if any
        reminder.description?.let { desc ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("📋 Details")
                    .addText(desc)
                    .build()
            )
        }
        
        // Time
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("⏰ Gepland")
                .addText(triggerTime)
                .build()
        )
        
        // Status
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📊 Status")
                .addText(status)
                .build()
        )
        
        // Toggle complete
        if (!reminder.isCompleted) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("✅ Markeer voltooid")
                    .setOnClickListener {
                        markCompleted()
                    }
                    .build()
            )
        }
        
        // Delete button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🗑️ Verwijder")
                .setOnClickListener {
                    deleteReminder()
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Herinnering")
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun markCompleted() {
        scope.launch {
            val updated = reminder.copy(isCompleted = true)
            reminderDao.updateReminder(updated)
            withContext(Dispatchers.Main) {
                onUpdated()
                screenManager.pop()
            }
        }
    }
    
    private fun deleteReminder() {
        scope.launch {
            reminderDao.deleteReminder(reminder)
            withContext(Dispatchers.Main) {
                onUpdated()
                screenManager.pop()
            }
        }
    }
}
