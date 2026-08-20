package com.example.carheadunit.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.carheadunit.R

/**
 * Event chimes via [SoundPool]: fire-and-forget playback mixed by the system
 * audio threads — zero load on the UI/telemetry threads. Tagged as
 * ASSISTANCE_SONIFICATION so chimes mix over whatever is playing without
 * stealing audio focus.
 */
class ChimePlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private var tickId = 0
    private var loaded = false

    init {
        tickId = soundPool.load(context, R.raw.tick, 1)
        soundPool.setOnLoadCompleteListener { _, id, status ->
            loaded = status == 0
            if (loaded) {
                Log.d(TAG, "Chime loaded (id=$id)")
            } else {
                Log.w(TAG, "Chime load failed: $status")
            }
        }
    }

    /** One indicator blip (turn signal tick / hazard click). */
    fun indicatorTick() {
        if (!loaded) return
        soundPool.play(tickId, 0.8f, 0.8f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }

    private companion object {
        const val TAG = "ChimePlayer"
    }
}
