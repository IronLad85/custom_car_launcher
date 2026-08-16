package com.example.carheadunit.ui.components

import android.content.Context
import android.content.Intent

/** Shared Android Auto / aftermarket link-app detection and launch. */
object AndroidAuto {

    val PACKAGES = listOf(
        "com.google.android.projection.gearhead", // Android Auto
        "com.zjinnova.zlink",                     // ZLink
        "com.autokit.linkkit",                    // AutoKit
        "com.easyconn",                           // EasyConnection
    )

    fun findPackage(context: Context): String? =
        PACKAGES.firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }

    fun launch(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
