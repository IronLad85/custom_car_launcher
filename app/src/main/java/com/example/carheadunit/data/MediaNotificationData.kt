package com.example.carheadunit.data

import android.app.PendingIntent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

enum class MediaActionType { PLAY_PAUSE, NEXT, PREV }

data class MediaAction(val type: MediaActionType, val intent: PendingIntent)

/** Live now-playing state extracted from the system media notification. */
data class MediaNotificationData(
    val key: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val albumArt: Bitmap?,
    val actions: List<MediaAction>,
)

/** Shared holder between MediaNotificationService and the ViewModel. */
object MediaNotificationHolder {
    val state = MutableStateFlow<MediaNotificationData?>(null)
    var key: String? = null

    fun update(data: MediaNotificationData?) {
        key = data?.key
        state.value = data
    }

    fun clearIf(key: String) {
        if (this.key == key) update(null)
    }
}
