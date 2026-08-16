package com.example.carheadunit.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A launchable app as shown by the launcher. The icon is a pre-decoded,
 * downscaled ImageBitmap cached by the repository — composition never hits
 * PackageManager or decodes drawables (important on weak head-unit SoCs).
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap,
)
