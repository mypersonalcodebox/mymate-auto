package com.mymate.auto.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mymate.auto.MainActivity
import com.mymate.auto.R
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.remote.WebSocketManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MyMateService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocketManager: WebSocketManager? = null
    private lateinit var preferencesManager: PreferencesManager
    
    companion object {
        const val CHANNEL_ID = "mymate_service_channel"
        const val NOTIFICATION_ID = 1001
        
        fun start(context: Context) {
            val intent = Intent(context, MyMateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, MyMateService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start WebSocket connection
        serviceScope.launch {
            val webhookUrl = preferencesManager.webhookUrl.first()
            val wsUrl = webhookUrl.replace("/auto", "").replace("http://", "ws://")
            
            webSocketManager = WebSocketManager(wsUrl)
            webSocketManager?.connect(autoReconnect = true)
            
            // Monitor incoming messages
            webSocketManager?.incomingMessages?.collect { message ->
                if (message.type == "notification" || message.type == "message") {
                    message.content?.let { content ->
                        showNotification(content)
                    }
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        webSocketManager?.cleanup()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MyMate Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Houdt MyMate verbonden op de achtergrond"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyMate")
            .setContentText("Verbonden en klaar")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun showNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyMate")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
