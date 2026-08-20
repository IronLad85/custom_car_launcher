package com.example.carheadunit.data

import android.content.Context
import java.time.LocalDate

/**
 * Derives "today km" from the live odometer with no trip signal on the bus:
 * the odometer reading at the start of each day is persisted, and today's
 * distance is current − day-start. The baseline re-arms when the date rolls
 * over or when the odometer moves backwards (replaced cluster / rollback).
 *
 * Limitation: distance only accumulates while the app is running — an app
 * restart mid-day re-bases from the current reading.
 */
class TodayKmTracker(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun todayKm(odometerKm: Float): Float {
        if (odometerKm <= 0f) return 0f // no live data yet
        val today = LocalDate.now().toString()
        val storedDate = prefs.getString(KEY_DATE, null)
        val baseline = prefs.getFloat(KEY_BASELINE_ODO, 0f)
        if (storedDate == today && baseline > 0f && baseline <= odometerKm) {
            return odometerKm - baseline
        }
        // New day, first sample ever, or odometer went backwards: re-base.
        prefs.edit()
            .putString(KEY_DATE, today)
            .putFloat(KEY_BASELINE_ODO, odometerKm)
            .apply()
        return 0f
    }

    private companion object {
        const val PREFS_NAME = "trip_prefs"
        const val KEY_DATE = "baseline_date"
        const val KEY_BASELINE_ODO = "baseline_odo_km"
    }
}
