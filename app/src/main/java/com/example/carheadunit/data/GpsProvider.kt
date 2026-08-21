package com.example.carheadunit.data

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import android.util.Log

/**
 * Thin GPS wrapper for the head unit's built-in receiver: keeps the latest
 * fix from LocationManager's GPS provider, refreshed at [UPDATE_INTERVAL_MS]
 * cadence. Recording attaches a fix only while it is fresh (≤ [MAX_AGE_MS]) —
 * a leftover fix from the last drive must never be stamped onto new rows.
 *
 * Safe to use without the location permission: start() logs and does
 * nothing, and currentFix() returns null (samples just omit GPS).
 */
class GpsProvider(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var thread: HandlerThread? = null

    @Volatile
    private var latest: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latest = location
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) {
            latest = null
        }
    }

    /** Starts GPS updates on a background thread; idempotent. */
    fun start() {
        if (thread != null) return
        if (!locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "No GPS provider on this device — samples will omit GPS")
            return
        }
        try {
            val t = HandlerThread("gps").apply { start() }
            thread = t
            // Throws SecurityException without the location permission. The
            // activity requests it at launch and re-invokes start() via
            // onLocationPermissionGranted(); GPS stays optional either way.
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                t.looper,
            )
            Log.i(TAG, "GPS tracking started (update every ${UPDATE_INTERVAL_MS / 1000}s)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing — samples will omit GPS")
        }
    }

    /** Latest fix, or null when stale or never fixed. */
    fun currentFix(): Location? {
        val fix = latest ?: return null
        return if (System.currentTimeMillis() - fix.time <= MAX_AGE_MS) fix else null
    }

    fun stop() {
        runCatching { locationManager.removeUpdates(listener) }
        thread?.quitSafely()
        thread = null
        latest = null
    }

    private companion object {
        const val TAG = "GpsProvider"
        // Fix refresh cadence: 10 s keeps GPS reads cheap while still
        // tracking the route (the hardware receiver runs regardless).
        const val UPDATE_INTERVAL_MS = 10_000L
        // A fix older than this is considered stale and not attached.
        const val MAX_AGE_MS = 60_000L
    }
}
