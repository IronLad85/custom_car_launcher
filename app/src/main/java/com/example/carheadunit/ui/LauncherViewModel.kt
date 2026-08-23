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
import com.example.carheadunit.data.ChimePlayer
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.data.MediaActionType
import com.example.carheadunit.data.MediaInfo
import com.example.carheadunit.data.MediaNotificationHolder
import com.example.carheadunit.data.MediaNotificationService
import com.example.carheadunit.data.TelemetryLogger
import com.example.carheadunit.data.TodayKmTracker
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
    // USB CAN Sniffer is the only data source; offline shows static defaults.
    private val usbSource = UsbEsp32DataSource(application)
    private val dataSource: CarDataSource = usbSource

    /** USB link health for the dock status chip. */
    val usbStatus = usbSource.status

    private val telemetryLogger = TelemetryLogger(application)

    // "Today km" is derived in the app: live odometer minus the persisted
    // day-start reading (no trip signal on this CAN bus).
    private val todayKmTracker = TodayKmTracker(application)

    // Turn-signal tick-tock: one chime per lamp rising edge (also serves as
    // the hazard click — both lamps blink together).
    private val chimePlayer = ChimePlayer(application)
    private var indicatorsOn = false
    private var lastChimeAt = 0L

    // True while another app is in front (main thread only): the telemetry
    // tick freezes so stale values aren't refreshed or logged in background.
    private var backgrounded = false

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
        // Log EVERY USB data frame (per-frame timestamps + changed signals) —
        // full-resolution data for the telemetry server.
        usbSource.frameListener = { ts, payload -> telemetryLogger.frame(ts, payload) }
        refreshApps()
        refreshMediaAccess()
        // Live now-playing: rebuild the media part of the snapshot the moment a
        // notification changes (no waiting for the 1 s tick).
        viewModelScope.launch {
            MediaNotificationHolder.state.collect {
                _snapshot.value = _snapshot.value.copy(media = mediaInfo())
            }
        }
        // Live telemetry tick: USB source when connected, static zeros offline.
        viewModelScope.launch {
            while (isActive) {
                // Frozen while another app is in front: no state refresh, no chimes.
                if (!backgrounded) {
                    val snap = dataSource.snapshot()
                    _snapshot.value = snap.copy(
                        media = mediaInfo(),
                        todayKm = todayKmTracker.todayKm(snap.odometerKm),
                    )
                    // Indicator chime on the lamp rising edge (debounced).
                    val indOn = snap.turnLeftLamp || snap.turnRightLamp
                    if (indOn && !indicatorsOn) {
                        val now = System.currentTimeMillis()
                        if (now - lastChimeAt >= CHIME_MIN_INTERVAL_MS) {
                            lastChimeAt = now
                            chimePlayer.indicatorTick()
                        }
                    }
                    indicatorsOn = indOn
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
        chimePlayer.release()
        super.onCleared()
    }

    /** Recording continues in the background: only the UI tick freezes. The
     *  USB stream and per-frame SQLite logging keep running on their own
     *  threads (cheaper than foreground — no recomposition, no chimes). */
    fun onBackground() {
        backgrounded = true
    }

    /** USB stream resumes on foreground (0x53, snapshot refresh). */
    fun onForeground() {
        backgrounded = false
        usbSource.resume()
        // The user may have just granted notification access in Settings
        refreshMediaAccess()
    }

    /** Routes the runtime location-permission result back to the logger so
     *  GPS tracking can start (the init-time attempt fails while the
     *  permission dialog is still pending). */
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) telemetryLogger.onLocationPermissionGranted()
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
            // Drop pins for entries that are no longer installed. Keys are
            // packageName/activityName, so old package-only pins from before
            // the identity fix are dropped here too.
            val installed = _apps.value.map { it.key }.toSet()
            if (_pinned.value != _pinned.value.intersect(installed)) {
                _pinned.value = _pinned.value.intersect(installed)
                persistPins()
            }
        }
    }

    fun togglePin(appKey: String) {
        val current = _pinned.value
        _pinned.value = if (appKey in current) current - appKey else current + appKey
        persistPins()
    }

    fun launchApp(app: AppEntry) = appsRepository.launch(app)

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
        // UI refresh cadence: ~8 Hz keeps the gauges responsive on live data.
        const val TICK_MS = 128L
        // Indicator chimes are edge-triggered per blink; debounce guards
        // against flapping lamp signals turning into a beep storm.
        const val CHIME_MIN_INTERVAL_MS = 250L
    }
}
