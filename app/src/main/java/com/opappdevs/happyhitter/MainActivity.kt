package com.opappdevs.happyhitter

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.opappdevs.happyhitter.const.WATCHER_WORKER_FLEX_MINUTES
import com.opappdevs.happyhitter.const.WATCHER_WORKER_REPEAT_MINUTES
import com.opappdevs.happyhitter.ui.theme.HappyHitterTheme
import com.opappdevs.happyhitter.util.NotificationListenerWatcherService
import com.opappdevs.happyhitter.util.NotificationListenerService
import com.opappdevs.happyhitter.util.NotificationListenerWatcherServiceWorker
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    private val tag = javaClass.simpleName

    private var _serviceComponentName: ComponentName? = null

    private var notificationPermissionGranted = mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                isGranted: Boolean ->
            if (isGranted) {
                Log.i(
                    tag, "POST_NOTIFICATION permission is granted, " +
                            "starting watcher service"
                )
                startWatcherServiceImplementation()
                updateNotificationPermissionDisplay(granted = true)
            } else {
                Log.w(
                    tag, "POST_NOTIFICATION permission not granted, " +
                            "cannot start watcher service"
                )
                Toast.makeText(
                    this, "Can't monitor\napp functionality",
                    Toast.LENGTH_SHORT
                ).show()
            }
            updateNotificationPermissionDisplay(granted = isGranted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // present ui
        enableEdgeToEdge()
        setContent {
            HappyHitterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Main(
                        description = "Listen for Now Playing",
                        modifier = Modifier.padding(innerPadding),
                        notificationPermissionGrantedState = notificationPermissionGranted,
                        onStart = { checkNotificationListenerPermission() },
                        onStartWatching = { startWatcherService() },
                        onStopWatching = { stopWatcherService() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startWatcherService()
    }

    private fun checkAndRequestNotificationPermission() {
        when {
            isNotificationPermissionGranted() -> {
                updateNotificationPermissionDisplay(granted = true)
                Log.i(tag, "POST_NOTIFICATION permission is granted, starting watcher service")
                startWatcherServiceImplementation()
            }
            else -> {
                stopWatcherService()
                showNotificationPermissionRationale()
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Berechtigung erforderlich")
            .setMessage("Diese App muss eine dauerhafte Benachrichtigung anzeigen, " +
                    "damit sie Now Playing überwachen kann. Möchten Sie dies erlauben?")
            .setPositiveButton("Ja") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Nein") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Can't monitor\napp functionality",
                    Toast.LENGTH_SHORT).show()
                updateNotificationPermissionDisplay(granted = false)
            }
            .show()
    }

    private fun updateNotificationPermissionDisplay(granted: Boolean) {
        notificationPermissionGranted.value = granted
    }


    // NOTIFICATIONS
    // TODO: regularly increment the priority of FG notification to avoid disappearing (MIUI/HyperOS)
    // TODO: regularly (60 min) update FG notification if disappeared
    // TODO: extract notification logic to separate service (DI)
    // TODO: display all recognized titles in notification
    // TODO: improve notifications for Wear OS: https://developer.android.com/develop/ui/views/notifications#wear
    // TODO: turn off watcher and listener via button in persistent notification

    // SERVICE CONTROL
    // TODO: allow listener service start only after key phrases have been set
    // TODO: Toast after successful initial NL launch (on return from settings)
    // TODO: check for battery optimization and inform the user (Not now, Take me there, Never show again -> prefs)
    // TODO: NL must get enabled setting from prefs or db (onStartCommand or onBind)
    // TODO: make sure NL is off when FG is off
    // TODO: regularly check and restart NL from FG or even self
    //  apply Medium stability measure
    // TODO: restart FG/NL immediately if stopped/destroyed (requestRebind, startForeground)
    //  via onDestroy or onDisconnected?
    // TODO: use a ServiceManager with its only public method startListening()
    //  this will start FG, which will start BG(s)
    //  and those BGs will, via prefs or db determine what to watch/listen to

    // DATA MANAGEMENT
    // TODO: set up db
    // TODO: store key/detected titles in a history (toggle via settings)
    //  highlight detected key phrase titles
    //  configure history length (days/weeks) via settings
    // TODO: settings store via prefs

    // UI
    // TODO: show permissions and services status in nav drawer or dedicated composable
    // TODO: input for key phrases
    // TODO: icon art and splash screen
    // TODO: color theme and dark mode
    // TODO: overflow menus?
    // TODO: settings composable
    // TODO: scaffold
    // TODO: complement Toasts with SnackBars and put into a SnackUtils class
    // TODO: use centered Toast: https://stackoverflow.com/questions/3522023/center-text-in-a-toast

    // SETTINGS
    // TODO: make alarm sound file and volume customizable, make sure original volume restores
    // TODO: prevent repeated alarms or customize period

    // NAVIGATION
    // TODO: compose navigation, nav drawer

    // ARCHITECTURE
    // TODO: dependency injection

    // FUTURE
    // TODO: integrate AI
    // TODO: make use of smartwatches (current title, disable listening, volume control)
    // TODO: develop for iOS (KMP)
    // TODO: complete title/artist via web db
    // TODO: download over art and use as background for detection notification for every title
    //  highlight detected key phrase title notifications
    // TODO: Google Play and App Store, Impressum mit Copyright und Lizenz, Datenschutzerklärung
    // TODO: display OSS libs and licenses in the app
    // TODO: implement support form with log sending
    // TODO: implement more sources such as Shazam, YT Music, Apple Music, iTunes, Spotify, popular players and direct streams (exploit metadata)
    // TODO: more actions on title: enqueue, show additional info (album, year etc.)


    private fun startWatcherService() {
        checkAndRequestNotificationPermission()
    }

    private fun startWatcherServiceImplementation() {
        if (!NotificationListenerWatcherService.isRunning) {
            val startIntent = Intent(this, NotificationListenerWatcherService::class.java)
            _serviceComponentName = startForegroundService(startIntent)
            Log.d(tag, "Service component name: $_serviceComponentName")
        }
        scheduleWorker()
    }

    private fun scheduleWorker() {
        val workManager = WorkManager.getInstance(applicationContext)

        // Check if the worker is already scheduled
        val workInfos = workManager.getWorkInfosForUniqueWork(
            NotificationListenerWatcherServiceWorker.WORK_NAME).get()
        if (workInfos.isNotEmpty() && workInfos[0].state == WorkInfo.State.ENQUEUED) {
            // Worker is already scheduled
            return
        }
        Log.d(tag, "Work not already enqueued, continuing scheduling")

        // Create the worker request
        val workRequest = PeriodicWorkRequestBuilder<NotificationListenerWatcherServiceWorker>(
            WATCHER_WORKER_REPEAT_MINUTES, TimeUnit.MINUTES,
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
        workManager.getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            // Service started successfully
                            Log.d(tag, "Successfully enqueued worker")
                        }
                        WorkInfo.State.FAILED -> {
                            val failureReason = workInfo.outputData.getString(
                                NotificationListenerWatcherServiceWorker.FAILURE_RESULT_KEY)
                            Log.e(tag, "Failed to enqueue worker: $failureReason")
                            // Handle the failure (e.g., show a notification to the user)
                            showFailureNotification(failureReason)
                        }
                        else -> {
                            // Handle other states if needed
                            Log.d(tag, "You need to handle additional states," +
                                    "the current state is ${workInfo.state}")
                        }
                    }
                } else {
                    Log.e(tag, "Error: workInfo is null")
                }
            }
    }

    private fun showFailureNotification(failureReason: String?) {
        Toast.makeText(this, "Starting the Watcher service failed\n" +
                "Reason: $failureReason", Toast.LENGTH_SHORT).show()
    }

    private fun stopWatcherService() {
        // TODO: we need to disable the worker
        if (!NotificationListenerWatcherService.isRunning) {
            return
        }
        val stopIntent = Intent(this, NotificationListenerWatcherService::class.java)

        if (stopService(stopIntent)) {
            Log.i(tag, "Watcher service successfully stopped")
        } else {
            Log.w(tag, "Watcher service could not be stopped")
        }
    }

    private fun checkNotificationListenerPermission() {
        if (!isNotificationListenerServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Berechtigung erforderlich")
                .setMessage("Diese App benötigt Zugriff auf Benachrichtigungen. " +
                        "Möchten Sie die Einstellungen öffnen?")
                .setPositiveButton("Ja") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Nein") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            Log.i(tag, "NotificationListenerService is registered")
        }
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = ComponentName.unflattenFromString(name)
                if (componentName != null && TextUtils.equals(pkgName, componentName.packageName)) {
                    return true
                }
            }
        }
        return false
    }

    // TODO: we need this?
//    @Suppress("DEPRECATION")
//    private fun isNLServiceRunning(context: Context): Boolean {
//        val manager = context.getSystemService(Service.ACTIVITY_SERVICE) as ActivityManager
//        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
//            if (NotificationListener::class.java.name == service.service.className) {
//                return true
//            }
//        }
//        return false
//    }
}

@Composable
fun Main(
    description: String,
    modifier: Modifier = Modifier,
    notificationPermissionGrantedState: MutableState<Boolean>,
    onStart: () -> Unit,
    onStartWatching: () -> Unit,
    onStopWatching: () -> Unit,
) {
    val context = LocalContext.current

    val notificationPermissionGranted by remember { notificationPermissionGrantedState }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Notification Permission:"
        )
        Text(
            text = if (notificationPermissionGranted) "GRANTED" else "DENIED",
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = description,
            modifier = Modifier.padding(top = 32.dp)
        )
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                Log.d("Composable_Main", "Start button pressed")
                onStart()
            }
        ) {
            Text(text = "Start")
        }
        Button(
            onClick = {
                Log.d("Composable_Main", "Check button pressed")
                if (NotificationListenerService.isServiceRunning) {
                    Log.i("Composable_Main", "NotificationListenerService is running")
                    Toast.makeText(
                        context, "Service running",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.w("Composable_Main","Service not running")
                    Toast.makeText(
                        context, "Service stopped",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Check")
        }
        Button(
            onClick = {
                Log.d("Composable_Main", "Watcher Start button pressed")
                onStartWatching()
            },
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Text(text = "Start Watcher")
        }
        Button(
            onClick = {
                Log.d("Composable_Main", "Watcher Stop button pressed")
                onStopWatching()
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Stop Watcher")
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    HappyHitterTheme {
//        Main("Android", Modifier) {}
//    }
//}