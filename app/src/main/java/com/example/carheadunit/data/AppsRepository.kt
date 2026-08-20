package com.example.carheadunit.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Collator

/**
 * Enumerates and launches installed apps. Visibility is granted by the
 * <queries> block in the manifest. App icons are decoded once into small
 * cached bitmaps (48dp @ 2x) — the drawer just draws them, so opening and
 * scrolling the app grid stays cheap on low-end head-unit SoCs.
 */
class AppsRepository(private val context: Context) {

    private val iconCache = HashMap<String, ImageBitmap>()

    /** All installed launchable apps (this launcher excluded), sorted alphabetically by label. */
    fun loadLaunchableApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return infos
            .mapNotNull { info -> info.toEntry(pm) }
            .filter { it.packageName != context.packageName }
            // One tile per launcher activity (like the system launcher): apps
            // such as Android Auto expose several entries.
            .sortedWith(compareBy(Collator.getInstance()) { it.label.lowercase() })
    }

    /** Starts the app's launcher activity. */
    fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun ResolveInfo.toEntry(pm: PackageManager): AppEntry? {
        val activity = activityInfo ?: return null
        return AppEntry(
            packageName = activity.packageName,
            activityName = activity.name,
            label = loadLabel(pm)?.toString() ?: activity.packageName,
            icon = cachedIcon(pm, activity.packageName),
        )
    }

    /** Decode once per package into a small bitmap; subsequent refreshes reuse it. */
    private fun cachedIcon(pm: PackageManager, packageName: String): ImageBitmap =
        iconCache.getOrPut(packageName) {
            val drawable = pm.getApplicationIcon(packageName)
            val size = (48 * context.resources.displayMetrics.density * 2).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }
}
