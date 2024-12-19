package com.opappdevs.happyhitter.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.opappdevs.happyhitter.const.WATCHER_WORKER_FLEX_MINUTES
import com.opappdevs.happyhitter.const.WATCHER_WORKER_PERIOD_MINUTES
import java.util.concurrent.TimeUnit

class BootCompletedReceiver : BroadcastReceiver() {

    private val tag = javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            startWatcherServiceImplementation(context)
        }
    }

    private fun startWatcherServiceImplementation(context: Context) {
        if (!NotificationListenerWatcherService.isRunning) {
            val startIntent = Intent(context, NotificationListenerWatcherService::class.java)
            val  serviceComponentName = ContextCompat.startForegroundService(context, startIntent)
            Log.d(tag, "Service component name: $serviceComponentName")
        }
        scheduleWorker(context)
    }

    private fun scheduleWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Check if the worker is already scheduled
        val workInfos = workManager.getWorkInfosForUniqueWork(
            NotificationListenerWatcherServiceWorker.WORK_NAME).get()
        if (workInfos.isNotEmpty() && workInfos[0].state == WorkInfo.State.ENQUEUED) {
            // Worker is already scheduled
            return
        }
        Log.d(tag, "Work not already enqueued, continuing scheduling")

        // Create the worker request
        val workRequest = PeriodicWorkRequest.Builder(
            NotificationListenerWatcherServiceWorker::class.java,
            WATCHER_WORKER_PERIOD_MINUTES, TimeUnit.MINUTES,
            WATCHER_WORKER_FLEX_MINUTES, TimeUnit.MINUTES  // min 300000 ms = 5 minutes
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
        Log.d(tag, "Work request created, continuing scheduling")

        // Enqueue the work request
        workManager.enqueueUniquePeriodicWork(
            NotificationListenerWatcherServiceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(tag, "Worker enqueued, continuing to observing")

        // Observe the work status
        // TODO: use Dependency Injection to initialize a ServiceManager with a suitable Lifecycle
//        workManager.getWorkInfoByIdLiveData(workRequest.id)
//            .observe(context) { workInfo ->
//                if (workInfo != null) {
//                    when (workInfo.state) {
//                        WorkInfo.State.ENQUEUED -> {
//                            // Service started successfully
//                            Log.d(tag, "Successfully enqueued worker")
//                        }
//                        WorkInfo.State.FAILED -> {
//                            val failureReason = workInfo.outputData.getString(
//                                NotificationListenerWatcherServiceWorker.FAILURE_RESULT_KEY)
//                            Log.e(tag, "Failed to enqueue worker: $failureReason")
//                            // Handle the failure (e.g., show a notification to the user)
//                            showFailureNotification(failureReason)
//                        }
//                        else -> {
//                            // Handle other states if needed
//                            Log.d(tag, "You need to handle additional states," +
//                                    "the current state is ${workInfo.state}")
//                        }
//                    }
//                } else {
//                    Log.e(tag, "Error: workInfo is null")
//                }
//            }
    }

//    private fun showFailureNotification(failureReason: String?) {
//        Toast.makeText(this, "Starting the Watcher service failed\n" +
//                "Reason: $failureReason", Toast.LENGTH_SHORT).show()
//    }
}
