package com.mymate.auto.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mymate.auto.data.model.Reminder

/**
 * Schedules reminder notifications using AlarmManager
 */
class ReminderScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "ReminderScheduler"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_DESCRIPTION = "reminder_description"
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * Schedule a notification for a reminder
     */
    fun schedule(reminder: Reminder) {
        // Don't schedule if already past
        if (reminder.triggerTime <= System.currentTimeMillis()) {
            Log.d(TAG, "Not scheduling reminder ${reminder.id} - time already passed")
            return
        }
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires checking canScheduleExactAlarms
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for reminder ${reminder.id} at ${reminder.triggerTime}")
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "Scheduled inexact alarm for reminder ${reminder.id} - exact alarms not permitted")
                }
            } else {
                // Pre-Android 12
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for reminder ${reminder.id} at ${reminder.triggerTime}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm: ${e.message}")
            // Fallback to inexact
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerTime,
                pendingIntent
            )
        }
    }
    
    /**
     * Cancel a scheduled reminder
     */
    fun cancel(reminderId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for reminder $reminderId")
    }
    
    /**
     * Reschedule all pending reminders (call after boot)
     */
    fun rescheduleAll(reminders: List<Reminder>) {
        val now = System.currentTimeMillis()
        reminders
            .filter { !it.isCompleted && it.triggerTime > now }
            .forEach { schedule(it) }
        Log.d(TAG, "Rescheduled ${reminders.size} reminders")
    }
}
