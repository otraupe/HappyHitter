package com.opappdevs.happyhitter.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.opappdevs.happyhitter.MainActivity
import com.opappdevs.happyhitter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationListenerWatcherService: Service() {

    private val tag = javaClass.simpleName

    enum class ListenerStatus(val statusText: String, val iconResId: Int) {
        STOPPED ("Notification Listener Service nicht gestartet",
            R.drawable.ic_listener_stopped),
        STARTED ("Notification Listener Service inaktiv",
            R.drawable.ic_listener_inactive),
        ACTIVE ("Notification Listener Service aktiv",
            R.drawable.ic_listener_active),
    }

    private var listenerStatus = ListenerStatus.STOPPED

    private val CHANNEL_ID = "ListenerCheckerNotification"
    private val NOTIFICATION_ID = 1
    private val FOREGROUND_USAGE = FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    private var _watcherJob: Job? = null

    private val _notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        Log.d(tag, "onCreate called")

        createNotificationChannel()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand called")

        if (!isRunning) {
            Log.d(tag, "Started with startId $startId")

            val notification = createNotification(getListenerServiceStatus())
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, FOREGROUND_USAGE)
            startWatchingNotificationListener()
            isRunning = true
        }
        return START_STICKY
    }

    // appears never to be called
//    override fun stopService(name: Intent?): Boolean {
//        Log.d(tag, "stopService called")
//
//        stopWatchingNotificationListener()
//        isRunning = false
//        return super.stopService(name)
//    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy called")

        stopWatchingNotificationListener()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null //binding not allowed
    }

    // TODO: regularly check for notification present and restore if necessary
    // TODO: extract notification logic to separate service (DI)
    private fun createNotificationChannel() {
        val name = "Notification Listener Überwachung"
        val descriptionText = "Zeigt an, ob der Notification Listener Service aktiv ist"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        _notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(listenerStatus: ListenerStatus): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Notification Listener Status")
            .setContentText(listenerStatus.statusText)
            .setSmallIcon(listenerStatus.iconResId)
            .setContentIntent(pendingIntent)
            .setOngoing(true)   //persistent, still dismissible?
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)  // skip 10s delay
            .build()
    }

    private fun startWatchingNotificationListener() {
        _watcherJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                Log.d(tag, "Watcher cycle started (5000ms)")
                delay(5000)
                val currentStatus = getListenerServiceStatus()
                if (currentStatus == listenerStatus) {
                    continue
                }
                listenerStatus = currentStatus
                presentNotification(currentStatus)
            }
        }
    }

    private fun stopWatchingNotificationListener() {
        _watcherJob?.cancel()
    }

    private fun getListenerServiceStatus(): ListenerStatus {
        return when (NotificationListenerService.isServiceRunning) {
            false -> ListenerStatus.STOPPED
            true -> when (NotificationListenerService.isListenerEnabled) {
                false ->ListenerStatus.STARTED
                true -> ListenerStatus.ACTIVE
            }
        }
    }

    private suspend fun presentNotification(listenerStatus: ListenerStatus) {
        withContext(Dispatchers.Main) {
            val notification = createNotification(listenerStatus)
            _notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        var isRunning = false
            private set
    }
}