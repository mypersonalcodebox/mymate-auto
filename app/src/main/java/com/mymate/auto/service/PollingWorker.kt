package com.mymate.auto.service

import android.content.Context
import androidx.work.*
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.remote.MyMateApiClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PollingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    private val preferencesManager = PreferencesManager(context)
    private val apiClient = MyMateApiClient()
    
    override suspend fun doWork(): Result {
        return try {
            val webhookUrl = preferencesManager.webhookUrl.first()
            
            // Check for new messages (ping the server)
            val result = apiClient.sendMessage(
                webhookUrl = webhookUrl,
                message = "__ping__",
                quickActionId = null
            )
            
            result.fold(
                onSuccess = { response ->
                    // If there's a meaningful response, we could notify the user
                    if (response.reply != null && response.reply != "pong" && response.reply.isNotBlank()) {
                        // Could trigger a notification here
                    }
                    Result.success()
                },
                onFailure = {
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        private const val WORK_NAME = "mymate_polling_worker"
        
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<PollingWorker>(
                30, TimeUnit.SECONDS,
                15, TimeUnit.SECONDS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }
        
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
