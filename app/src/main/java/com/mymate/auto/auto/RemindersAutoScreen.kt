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

/**
 * Android Auto Reminders Screen - Todo-list style
 * 
 * Features:
 * - Quick-add buttons at top (5 min, 15 min, 1 hour, custom)
 * - List of pending reminders (tap to mark complete/delete)
 * - Toggle to show/hide completed reminders
 */
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
        
        // === QUICK ADD SECTION ===
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏱️ 5 min")
                .addText("Snel herinnering in 5 minuten")
                .setBrowsable(true)
                .setOnClickListener {
                    navigateToVoiceInput(5, "Over 5 minuten")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏱️ 15 min")
                .addText("Snel herinnering in 15 minuten")
                .setBrowsable(true)
                .setOnClickListener {
                    navigateToVoiceInput(15, "Over 15 minuten")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏱️ 1 uur")
                .addText("Snel herinnering in 1 uur")
                .setBrowsable(true)
                .setOnClickListener {
                    navigateToVoiceInput(60, "Over 1 uur")
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📝 Custom")
                .addText("Aangepaste tijd instellen")
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
        
        // === TOGGLE SECTION ===
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (showCompleted) "👁️ Verberg voltooide" else "👁️ Toon voltooide")
                .addText("─────────────────────")
                .setOnClickListener {
                    showCompleted = !showCompleted
                    isLoading = true
                    invalidate()
                    loadReminders()
                }
                .build()
        )
        
        // === REMINDERS LIST ===
        if (reminders.isEmpty()) {
            val emptyText = if (showCompleted) {
                "Nog geen herinneringen aangemaakt"
            } else {
                "Geen openstaande herinneringen ✨"
            }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("📭 Leeg")
                    .addText(emptyText)
                    .build()
            )
        } else {
            // Show up to 5 reminders (Android Auto limit)
            reminders.take(5).forEach { reminder ->
                val statusEmoji = if (reminder.isCompleted) "✅" else "⏰"
                val timeText = formatReminderTime(reminder.triggerTime)
                val title = reminder.title.take(35)
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("$statusEmoji $title")
                        .addText(timeText)
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(
                                ReminderDetailScreen(carContext, reminder, reminderDao) {
                                    loadReminders()
                                }
                            )
                        }
                        .build()
                )
            }
            
            // Show overflow indicator
            if (reminders.size > 5) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("📋 +${reminders.size - 5} meer")
                        .addText("Bekijk alle in de telefoon app")
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
    
    private fun navigateToVoiceInput(minutes: Int, timeLabel: String) {
        screenManager.push(
            VoiceInputScreen(carContext, "reminder_quick") { text ->
                addReminder(text, minutes, timeLabel)
            }
        )
    }
    
    private fun addReminder(title: String, minutesFromNow: Int, timeLabel: String) {
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
                
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(
                            carContext,
                            "✅ Herinnering toegevoegd",
                            "$timeLabel:\n\n\"$title\""
                        ) {
                            screenManager.popToRoot()
                            screenManager.push(RemindersAutoScreen(carContext))
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding reminder", e)
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "❌ Fout", "Kon herinnering niet opslaan") {
                            screenManager.pop()
                        }
                    )
                }
            }
        }
    }
    
    private fun addReminderWithParsing(text: String) {
        val lower = text.lowercase()
        
        // Parse time from text
        val minutesFromNow = when {
            lower.contains("5 min") -> 5
            lower.contains("10 min") -> 10
            lower.contains("15 min") -> 15
            lower.contains("30 min") || lower.contains("half uur") -> 30
            lower.contains("1 uur") || lower.contains("een uur") -> 60
            lower.contains("2 uur") || lower.contains("twee uur") -> 120
            lower.contains("3 uur") || lower.contains("drie uur") -> 180
            lower.contains("morgen") -> 24 * 60
            lower.contains("vanavond") -> calculateMinutesUntil(20, 0) // 8 PM
            lower.contains("vanmiddag") -> calculateMinutesUntil(14, 0) // 2 PM
            else -> 30 // Default to 30 minutes
        }
        
        // Clean up title by removing time indicators
        val cleanTitle = text
            .replace(Regex("over \\d+ min(uten)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("over \\d+ uur", RegexOption.IGNORE_CASE), "")
            .replace(Regex("in \\d+ min(uten)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("in \\d+ uur", RegexOption.IGNORE_CASE), "")
            .replace("morgen", "", ignoreCase = true)
            .replace("vanavond", "", ignoreCase = true)
            .replace("vanmiddag", "", ignoreCase = true)
            .replace("herinner me aan", "", ignoreCase = true)
            .replace("herinner me", "", ignoreCase = true)
            .replace("reminder", "", ignoreCase = true)
            .trim()
        
        val finalTitle = cleanTitle.ifEmpty { text }
        
        val timeLabel = when (minutesFromNow) {
            5 -> "Over 5 minuten"
            10 -> "Over 10 minuten"
            15 -> "Over 15 minuten"
            30 -> "Over 30 minuten"
            60 -> "Over 1 uur"
            120 -> "Over 2 uur"
            180 -> "Over 3 uur"
            else -> "Over $minutesFromNow minuten"
        }
        
        addReminder(finalTitle, minutesFromNow, timeLabel)
    }
    
    private fun calculateMinutesUntil(hour: Int, minute: Int): Int {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        
        // If target time has passed, set for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val diffMs = target.timeInMillis - now.timeInMillis
        return (diffMs / (60 * 1000)).toInt()
    }
    
    private fun formatReminderTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now
        
        return when {
            diff < 0 -> "⚠️ Verlopen: ${dateFormat.format(Date(timestamp))}"
            diff < 60 * 1000 -> "🔔 < 1 minuut"
            diff < 60 * 60 * 1000 -> "⏳ Over ${diff / (60 * 1000)} min"
            diff < 24 * 60 * 60 * 1000 -> "📅 Vandaag ${timeFormat.format(Date(timestamp))}"
            diff < 48 * 60 * 60 * 1000 -> "📅 Morgen ${timeFormat.format(Date(timestamp))}"
            else -> "📆 ${dateFormat.format(Date(timestamp))}"
        }
    }
}

/**
 * Detail screen for a single reminder
 * Allows marking complete or deleting
 */
class ReminderDetailScreen(
    carContext: CarContext,
    private val reminder: Reminder,
    private val reminderDao: com.mymate.auto.data.local.ReminderDao,
    private val onUpdated: () -> Unit
) : Screen(carContext) {
    
    private val TAG = "ReminderDetailScreen"
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
        val isPast = reminder.triggerTime < System.currentTimeMillis()
        
        val paneBuilder = Pane.Builder()
        
        // Title row
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📝 ${reminder.title}")
                .build()
        )
        
        // Time row
        val timeStatus = when {
            reminder.isCompleted -> "✅ Voltooid"
            isPast -> "⚠️ Verlopen"
            else -> "⏰ Gepland"
        }
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(timeStatus)
                .addText(triggerTime)
                .build()
        )
        
        // Description if present
        reminder.description?.let { desc ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("📋 Details")
                    .addText(desc.take(100))
                    .build()
            )
        }
        
        // Action buttons
        if (!reminder.isCompleted) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("✅ Voltooid")
                    .setOnClickListener { markCompleted() }
                    .build()
            )
        }
        
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🗑️ Verwijder")
                .setOnClickListener { deleteReminder() }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Herinnering")
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun markCompleted() {
        scope.launch {
            try {
                val updated = reminder.copy(isCompleted = true)
                reminderDao.updateReminder(updated)
                withContext(Dispatchers.Main) {
                    onUpdated()
                    screenManager.pop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking reminder complete", e)
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "❌ Fout", "Kon niet bijwerken") {
                            screenManager.pop()
                        }
                    )
                }
            }
        }
    }
    
    private fun deleteReminder() {
        scope.launch {
            try {
                reminderDao.deleteReminder(reminder)
                withContext(Dispatchers.Main) {
                    onUpdated()
                    screenManager.pop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting reminder", e)
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "❌ Fout", "Kon niet verwijderen") {
                            screenManager.pop()
                        }
                    )
                }
            }
        }
    }
}
