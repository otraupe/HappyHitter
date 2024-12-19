package com.opappdevs.happyhitter.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.opappdevs.happyhitter.R


class NotificationListenerService : NotificationListenerService() {

    private val tag = javaClass.simpleName

    private val targetPackageName = "com.google.android.as"
    //the target app name would be "Android System Intelligence"
    //the app name in the notification appears to be custom set as "Now Playing"
    private val targetChannelId = "com.google.intelligence.sense.ambientmusic.MusicNotificationChannel"

    private val _componentName: ComponentName by lazy {
        ComponentName(this, this::class.java)
    }

    private val listOfTitles = listOf(
        "wake me up before",
        "take on me",
        "all i want for christmas"
    )

    override fun onCreate() {
        Log.d(tag, "onCreate called")
        super.onCreate()
        isServiceRunning = true
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy called")
        super.onDestroy()
        isServiceRunning = false
    }

    private fun ensureNotificationListenerRegistered() {
        registerComponent(_componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        registerComponent(_componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    private fun registerComponent(componentName: ComponentName, enabled: Int) {
        componentName.let {
            packageManager.setComponentEnabledSetting(
                componentName,
                enabled,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.i(tag, "Notification detected")

        if (!isListenerEnabled) {
            Log.d(tag, "Listener disabled, not handling notification")
            return
        }

        val packageName = sbn.packageName
        Log.i(tag, "Package: $packageName")

        val appName = getAppNameFromPackage(packageName) //Android System Intelligence
        Log.i(tag, "App name: $appName")

        val notification = sbn.notification
        val channelId = notification.channelId
        Log.i(tag, "Channel: $channelId")

        if (packageName == targetPackageName && channelId == targetChannelId) {
            val nowPlaying = notification.extras.getString("android.title")
            val (title, artist) = try {
                nowPlaying!!.split(" by ")
            } catch (t: Throwable) {
                t.printStackTrace()
                listOf("Unknown Title","Unknown Artist")
            }
            handleNowPlayingNotification(artist, title)
        }
    }

    private fun handleNowPlayingNotification(artist: String, title: String) {
        Log.i(tag, "Now Playing: $title by $artist")
        val match = listOfTitles.filter { title.contains(it, ignoreCase = true) }
        if (match.isNotEmpty()) {
            playLoudNotificationSound(this, R.raw.alarm)
        }
    }

    private fun playLoudNotificationSound(context: Context, soundResourceId: Int) {
        try {
            // set notification volume to max
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxVolume, 0)

            // create MediaPlayer and play sound
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setDataSource(context, Uri.parse("android.resource://${context.packageName}/$soundResourceId"))
                prepare()
                start()
            }

            // release resources and revert volume
            mediaPlayer.setOnCompletionListener { mp ->
                mp.release()
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, previousVolume, 0)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        val pm = applicationContext.packageManager
        val ai = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return (if (ai != null) pm.getApplicationLabel(ai) else "(unknown)") as String
    }

    companion object {
        var isServiceRunning = false
            private set

        var isListenerEnabled = true
            private set

        fun disableListener() {
            isListenerEnabled = false
        }
        fun enableListener() {
            isListenerEnabled = true
        }
    }
}