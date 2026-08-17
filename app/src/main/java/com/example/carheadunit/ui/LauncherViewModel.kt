package com.example.carheadunit.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.data.AppsRepository
import com.example.carheadunit.data.CarDataSource
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.data.MediaActionType
import com.example.carheadunit.data.MediaInfo
import com.example.carheadunit.data.MediaNotificationHolder
import com.example.carheadunit.data.MediaNotificationService
import com.example.carheadunit.data.TelemetryLogger
import com.example.carheadunit.data.UsbEsp32DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepository = AppsRepository(application)
    // USB CAN Sniffer when attached; HTTP bridge (emulator dev) then mock as fallbacks.
    private val usbSource = UsbEsp32DataSource(application)
    private val dataSource: CarDataSource = usbSource

    /** USB link health for the dock status chip. */
    val usbStatus = usbSource.status

    private val telemetryLogger = TelemetryLogger(application)

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _pinned = MutableStateFlow(
        prefs.getStringSet(KEY_DOCK_PINS, emptySet())?.toSet() ?: emptySet()
    )
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    private val _snapshot = MutableStateFlow(CarSnapshot())
    val snapshot: StateFlow<CarSnapshot> = _snapshot.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _mediaAccess = MutableStateFlow(false)
    val mediaAccess: StateFlow<Boolean> = _mediaAccess.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    init {
        refreshApps()
        refreshMediaAccess()
        // Live now-playing: rebuild the media part of the snapshot the moment a
        // notification changes (no waiting for the 1 s tick).
        viewModelScope.launch {
            MediaNotificationHolder.state.collect {
                _snapshot.value = _snapshot.value.copy(media = mediaInfo())
            }
        }
        // Simulated telemetry tick
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                _snapshot.value = dataSource.snapshot().copy(media = mediaInfo())
                tickCount++
                // Log ALL received signals every 10 s (interval, not per message)
                if (tickCount % LOG_SAMPLE_TICKS == 0) {
                    dataSource.signalDump()?.let { telemetryLogger.sample(it) }
                }
                delay(TICK_MS)
            }
        }
        // Refresh the app list when anything is installed, removed, or updated
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(packageReceiver, filter)
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
        usbSource.close()
        telemetryLogger.close()
        super.onCleared()
    }

    /** USB stream pauses while the app is backgrounded (0x50). */
    fun onBackground() {
        usbSource.pause()
    }

    /** USB stream resumes on foreground (0x53, snapshot refresh). */
    fun onForeground() {
        usbSource.resume()
        // The user may have just granted notification access in Settings
        refreshMediaAccess()
    }

    fun refreshMediaAccess() {
        val app = getApplication<Application>()
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        _mediaAccess.value = nm.isNotificationListenerAccessGranted(
            android.content.ComponentName(app, MediaNotificationService::class.java),
        )
    }

    /** Opens the system "Notification access" settings page. */
    fun requestMediaAccess() {
        getApplication<Application>().startActivity(
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Sends a playback command through the media notification's own action intent. */
    fun mediaControl(type: MediaActionType) {
        MediaNotificationHolder.state.value?.actions
            ?.firstOrNull { it.type == type }
            ?.intent
            ?.send()
    }

    private fun mediaInfo(): MediaInfo {
        val data = MediaNotificationHolder.state.value
            ?: return MediaInfo("Nothing playing", "Start music on your phone", isPlaying = false)
        return MediaInfo(
            trackTitle = data.title,
            artist = data.artist,
            isPlaying = data.isPlaying,
            albumArt = data.albumArt?.asImageBitmap(),
        )
    }

    fun refreshApps() {
        // Icon decoding + package queries are the slow part on weak SoCs — off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = appsRepository.loadLaunchableApps()
            // Drop pins for apps that are no longer installed
            val installed = _apps.value.map { it.packageName }.toSet()
            if (_pinned.value != _pinned.value.intersect(installed)) {
                _pinned.value = _pinned.value.intersect(installed)
                persistPins()
            }
        }
    }

    fun togglePin(packageName: String) {
        val current = _pinned.value
        _pinned.value = if (packageName in current) current - packageName else current + packageName
        persistPins()
    }

    fun launchApp(app: AppEntry) = appsRepository.launch(app)

    fun togglePlayback() {
        dataSource.togglePlayback()
        _snapshot.value = dataSource.snapshot()
    }

    fun openDrawer() {
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
    }

    private fun persistPins() {
        // Copy the set: SharedPreferences keeps a reference to the instance it returns.
        prefs.edit().putStringSet(KEY_DOCK_PINS, _pinned.value.toHashSet()).apply()
    }

    private companion object {
        const val PREFS_NAME = "launcher_prefs"
        const val KEY_DOCK_PINS = "dock_pins"
        const val TICK_MS = 1000L
        const val LOG_SAMPLE_TICKS = 10
    }
}
