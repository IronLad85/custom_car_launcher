package com.example.carheadunit.data

import kotlin.random.Random

data class SpeedInfo(val kmh: Int, val unit: String = "km/h")

data class ClimateInfo(val tempC: Int, val fanLevel: Int, val fanMax: Int = 8)

data class MediaInfo(
    val trackTitle: String,
    val artist: String,
    val isPlaying: Boolean,
    val albumArt: androidx.compose.ui.graphics.ImageBitmap? = null,
)

/** One frame of car telemetry shown in the top cards. */
data class CarSnapshot(
    val speed: SpeedInfo = SpeedInfo(kmh = 0),
    val climate: ClimateInfo = ClimateInfo(tempC = 22, fanLevel = 4),
    val media: MediaInfo = MediaInfo(trackTitle = "Midnight Drive", artist = "Neon Skyline", isPlaying = true),
    // ESP32 telemetry (defaults keep the design's static look when offline)
    val power: Float = 0.42f,              // 0..1 derived: throttle × rpm/redline
    val steeringFraction: Float = 0.65f,   // 0..1 across the steering track
    val highBeam: Boolean = false,
    val turnLeftLamp: Boolean = false,
    val turnRightLamp: Boolean = false,
    val fogLight: Boolean = false,
    val chargeWarning: Boolean = false,
    val fuelLevel: Float = 1f,             // 0..1
    val batteryVoltage: Float = 12f,
)

/**
 * Source of live car data. Mock implementation for now; a real CAN/OBD provider
 * would implement the same interface.
 */
interface CarDataSource {
    fun snapshot(): CarSnapshot
    fun togglePlayback()

    /** Serialized dump of ALL received signals (for telemetry logging); null when unavailable. */
    fun signalDump(): String? = null
}

/** Simulates telemetry: speed random-walks, temperature drifts, fan level occasionally changes. */
class MockCarDataSource : CarDataSource {

    private var speedKmh = 62
    private var tempC = 22
    private var fanLevel = 4
    private var playing = true

    override fun snapshot(): CarSnapshot {
        speedKmh = (speedKmh + Random.nextInt(-6, 7)).coerceIn(0, 160)
        tempC = (tempC + Random.nextInt(-1, 2)).coerceIn(18, 27)
        if (Random.nextInt(40) == 0) {
            fanLevel = (fanLevel + Random.nextInt(-1, 2)).coerceIn(1, 8)
        }
        return CarSnapshot(
            speed = SpeedInfo(kmh = speedKmh),
            climate = ClimateInfo(tempC = tempC, fanLevel = fanLevel),
            media = MediaInfo(
                trackTitle = "Midnight Drive",
                artist = "Neon Skyline",
                isPlaying = playing,
            ),
        )
    }

    override fun togglePlayback() {
        playing = !playing
    }
}
