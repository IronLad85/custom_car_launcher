package com.example.carheadunit.data

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
 * Source of live car data. The only implementation is the USB CAN Sniffer
 * ([UsbEsp32DataSource]) — no HTTP bridge or simulator anymore.
 */
interface CarDataSource {
    fun snapshot(): CarSnapshot

    /** Serialized dump of ALL received signals (for telemetry logging); null when unavailable. */
    fun signalDump(): String? = null
}
