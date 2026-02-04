package com.mymate.auto

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.mymate.auto.service.MyMateService
import com.mymate.auto.service.PollingWorker
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyMateApplication : Application() {
    
    companion object {
        private const val TAG = "MyMateApp"
        private const val MAX_CRASH_FILES = 5
        
        fun getCrashLogsDir(app: Application): File {
            return File(app.filesDir, "crash_logs").apply { mkdirs() }
        }
        
        fun getLatestCrashLog(app: Application): String? {
            val dir = getCrashLogsDir(app)
            return dir.listFiles()
                ?.filter { it.extension == "txt" }
                ?.maxByOrNull { it.lastModified() }
                ?.readText()
        }
        
        fun getAllCrashLogs(app: Application): List<Pair<String, String>> {
            val dir = getCrashLogsDir(app)
            return dir.listFiles()
                ?.filter { it.extension == "txt" }
                ?.sortedByDescending { it.lastModified() }
                ?.map { it.name to it.readText() }
                ?: emptyList()
        }
        
        fun clearCrashLogs(app: Application) {
            getCrashLogsDir(app).listFiles()?.forEach { it.delete() }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        setupCrashHandler()
        createNotificationChannels()
        
        // Schedule background polling
        PollingWorker.schedule(this)
        
        Log.i(TAG, "MyMate Application started - v2.9 with crash logging")
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash log", e)
            }
            
            // Call default handler to show crash dialog / terminate app
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        Log.i(TAG, "Crash handler installed")
    }
    
    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val crashDir = getCrashLogsDir(this)
        val crashFile = File(crashDir, "crash_$timestamp.txt")
        
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        
        pw.println("=== MYMATE CRASH LOG ===")
        pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        pw.println("Thread: ${thread.name} (${thread.id})")
        pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        pw.println("App: ${packageName}")
        pw.println()
        pw.println("=== EXCEPTION ===")
        throwable.printStackTrace(pw)
        pw.println()
        pw.println("=== CAUSE CHAIN ===")
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 10) {
            pw.println("Caused by (${depth + 1}):")
            cause.printStackTrace(pw)
            cause = cause.cause
            depth++
        }
        
        crashFile.writeText(sw.toString())
        Log.e(TAG, "Crash log saved to: ${crashFile.absolutePath}")
        
        // Keep only last N crash files
        cleanupOldCrashLogs()
    }
    
    private fun cleanupOldCrashLogs() {
        val crashDir = getCrashLogsDir(this)
        val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        
        files.drop(MAX_CRASH_FILES).forEach { it.delete() }
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
