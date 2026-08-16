package com.example.carheadunit.data

import android.graphics.drawable.Drawable

/** A launchable app as shown by the launcher. Icon is pre-resolved so composition never hits PackageManager. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
)
