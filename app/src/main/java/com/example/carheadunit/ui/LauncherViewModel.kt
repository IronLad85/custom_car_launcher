package com.example.carheadunit.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.data.AppsRepository
import com.example.carheadunit.data.CarDataSource
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.data.Esp32DataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepository = AppsRepository(application)
    // Live ESP32 telemetry via the Mac-side bridge; falls back to mock when unreachable.
    private val dataSource: CarDataSource = Esp32DataSource()

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

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    init {
        refreshApps()
        // Simulated telemetry tick
        viewModelScope.launch {
            while (isActive) {
                _snapshot.value = dataSource.snapshot()
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
        super.onCleared()
    }

    fun refreshApps() {
        _apps.value = appsRepository.loadLaunchableApps()
        // Drop pins for apps that are no longer installed
        val installed = _apps.value.map { it.packageName }.toSet()
        if (_pinned.value != _pinned.value.intersect(installed)) {
            _pinned.value = _pinned.value.intersect(installed)
            persistPins()
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
        const val TICK_MS = 700L
    }
}
