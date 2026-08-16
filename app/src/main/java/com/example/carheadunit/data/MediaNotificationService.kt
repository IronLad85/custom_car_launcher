package com.example.carheadunit.data

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

/**
 * Reads the active media notification (title/artist/album art/play state/
 * playback actions) and publishes it to [MediaNotificationHolder]. Requires
 * the "Notification access" special permission — the tile prompts for it.
 */
class MediaNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (!isMediaNotification(n)) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank()) return

        // Album art: EXTRA_PICTURE is the media-style art (may be absent for some apps)
        val art: Bitmap? =
            extras.getParcelable(Notification.EXTRA_PICTURE)
                ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON)

        val actions = n.actions?.mapNotNull { a ->
            val titleLower = a.title?.toString()?.lowercase() ?: ""
            val type = when {
                titleLower.contains("next") -> MediaActionType.NEXT
                titleLower.contains("previous") || titleLower.contains("prev") ->
                    MediaActionType.PREV
                titleLower.contains("play") || titleLower.contains("pause") ->
                    MediaActionType.PLAY_PAUSE
                else -> null
            }
            if (type != null) MediaAction(type, a.actionIntent) else null
        } ?: emptyList()

        // Playing if the app exposes a "Pause" action right now
        val isPlaying = actions.any { it.type == MediaActionType.PLAY_PAUSE } &&
            n.actions?.any {
                it.title?.toString()?.equals("pause", ignoreCase = true) == true ||
                    it.title?.toString()?.equals("pausa", ignoreCase = true) == true
            } == true

        Log.d(TAG, "Media: \"$title\" by $artist playing=$isPlaying actions=${actions.map { it.type }}")
        MediaNotificationHolder.update(
            MediaNotificationData(
                key = sbn.key,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                albumArt = art,
                actions = actions,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (isMediaNotification(n)) {
            MediaNotificationHolder.clearIf(sbn.key)
        }
    }

    /** Public-API heuristic: transport-category notifications or any carrying a media session. */
    private fun isMediaNotification(n: Notification): Boolean =
        n.category == Notification.CATEGORY_TRANSPORT ||
            n.extras.containsKey("android.mediaSession")

    private companion object {
        const val TAG = "MediaNotificationService"
    }
}
