package com.mymate.auto

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mymate.auto.service.MyMateService
import com.mymate.auto.service.PollingWorker

class MyMateApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannels()
        
        // Schedule background polling
        PollingWorker.schedule(this)
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                MyMateService.CHANNEL_ID,
                "MyMate Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Houdt MyMate verbonden"
                setShowBadge(false)
            }
            
            val messageChannel = NotificationChannel(
                "mymate_messages",
                "Berichten",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Berichten van MyMate"
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }
}
