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
    val climate: ClimateInfo = ClimateInfo(tempC = 0, fanLevel = 0),
    val media: MediaInfo = MediaInfo(trackTitle = "Nothing playing", artist = "Start music on your phone", isPlaying = false),
    // ESP32 telemetry. Every value stays at its zero state until the USB
    // source reports the corresponding signal — no mock readings.
    val rpm: Float = 0f,                  // ENGINE_RPM in rpm
    val throttle: Float = 0f,             // THROTTLE in %
    val odometerKm: Float = 0f,           // ODOMETER in km
    val todayKm: Float = 0f,              // derived in the app: odometer − day-start baseline
    val steeringFraction: Float = 0.5f,   // 0..1 across the steering track; 0.5 = centered (no data)
    val highBeam: Boolean = false,
    val turnLeftLamp: Boolean = false,
    val turnRightLamp: Boolean = false,
    val hazardMode: Boolean = false,
    val fogLight: Boolean = false,
    val chargeWarning: Boolean = false,
    val fuelLevel: Float = 0f,            // litres (0..126, 1 L resolution)
    val batteryVoltage: Float = 0f,
)

/**
 * Source of live car data. The only implementation is the USB CAN Sniffer
 * ([UsbEsp32DataSource]) — no HTTP bridge or simulator anymore.
 */
interface CarDataSource {
    fun snapshot(): CarSnapshot
}
