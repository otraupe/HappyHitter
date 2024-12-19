package com.opappdevs.happyhitter.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class NotificationListenerWatcherServiceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val foregroundServiceClass = NotificationListenerWatcherService::class.java
    private val tag = javaClass.simpleName

    companion object {
        const val WORK_NAME = "ForegroundServiceWorker"
        private const val SERVICE_START_TIMEOUT = 10000L // 10 seconds
        const val MAX_RETRIES = 3
        const val FAILURE_RESULT_KEY = "failure_reason"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        Log.d(tag, "doWork called")
        try {
            if (!isForegroundServiceRunning(foregroundServiceClass)) {
                Log.d(tag, "Watcher service not running, attempting to start")

                val intent = Intent(applicationContext, foregroundServiceClass)
                ContextCompat.startForegroundService(applicationContext, intent)

                if (waitForServiceToStart()) {
                    Result.success()
                } else {
                    handleRetryOrFailure("Service failed to start within timeout")
                }
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting foreground service", e)
            handleRetryOrFailure("Exception occurred: ${e.message}")
        }
    }

    private suspend fun waitForServiceToStart(): Boolean = withContext(Dispatchers.Default) {
        Log.d(tag, "Waiting for service to start")

        var attempts = 0
        val maxAttempts = 10
        val delayBetweenAttempts = SERVICE_START_TIMEOUT / maxAttempts

        while (attempts < maxAttempts) {
            if (isForegroundServiceRunning(foregroundServiceClass)) {
                return@withContext true
            }
            Log.d(tag, "Foreground service not running, checking again in $delayBetweenAttempts seconds")
            delay(delayBetweenAttempts)
            attempts++
        }
        false
    }

    private fun isForegroundServiceRunning(serviceClass: Class<*>): Boolean {
        // TODO: Send a broadcast when the service starts or stops; alternatively set a flag in
        //  SharedPreferences or DataStore
        // for now we'll just wing it
        return NotificationListenerWatcherService.isRunning
    }

    private fun handleRetryOrFailure(failureReason: String): Result {
        val runAttemptCount = runAttemptCount
        return if (runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            val outputData = workDataOf(FAILURE_RESULT_KEY to failureReason)
            Result.failure(outputData)
        }
    }
}
