package com.example.carheadunit.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.text.Collator

/** Enumerates and launches installed apps. Visibility is granted by the <queries> block in the manifest. */
class AppsRepository(private val context: Context) {

    /** All installed launchable apps (this launcher excluded), sorted alphabetically by label. */
    fun loadLaunchableApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return infos
            .mapNotNull { info -> info.toEntry(pm) }
            .filter { it.packageName != context.packageName }
            // One tile per app: queryIntentActivities lists every launchable activity,
            // so keep the first (default) entry per package.
            .distinctBy { it.packageName }
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
            icon = loadIcon(pm),
        )
    }
}
