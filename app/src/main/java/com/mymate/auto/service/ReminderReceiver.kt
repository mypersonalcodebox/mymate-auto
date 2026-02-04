package com.mymate.auto.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mymate.auto.MainActivity
import com.mymate.auto.R

/**
 * Receives reminder alarms and shows notifications
 */
class ReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "reminder_notifications"
        private const val CHANNEL_NAME = "Herinneringen"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Herinnering"
        val description = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION)
        
        Log.d(TAG, "Received reminder alarm: id=$reminderId, title=$title")
        
        if (reminderId == -1L) {
            Log.e(TAG, "Invalid reminder ID")
            return
        }
        
        showNotification(context, reminderId, title, description)
    }
    
    private fun showNotification(
        context: Context,
        reminderId: Long,
        title: String,
        description: String?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setDescription("Meldingen voor herinneringen")
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent to open app when notification is tapped
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reminders")
            putExtra("reminder_id", reminderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(description ?: "Tijd voor je herinnering!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        notificationManager.notify(reminderId.toInt(), notification)
        Log.d(TAG, "Showed notification for reminder $reminderId")
    }
}
