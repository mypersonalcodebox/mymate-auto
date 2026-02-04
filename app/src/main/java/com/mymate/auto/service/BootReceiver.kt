package com.mymate.auto.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mymate.auto.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // Reschedule polling worker
            PollingWorker.schedule(context)
            
            // Reschedule all pending reminders
            rescheduleReminders(context)
        }
    }
    
    private fun rescheduleReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getInstance(context)
            val reminders = database.reminderDao().getPendingReminders()
            val scheduler = ReminderScheduler(context)
            scheduler.rescheduleAll(reminders)
        }
    }
}
