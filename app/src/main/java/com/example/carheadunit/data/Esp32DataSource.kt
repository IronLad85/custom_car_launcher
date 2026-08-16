package com.example.carheadunit.data

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Reads live telemetry from the ESP32 mock via the Mac-side bridge:
 * the bridge serves the latest CBOR-decoded sensor values as JSON on
 * http://10.0.2.2:8080/state (10.0.2.2 = the host machine from the emulator).
 * Falls back to the mock simulator while the bridge is unreachable.
 *
 * The ESP32 firmware emits normalized values, so they are scaled here:
 * SPEED 0..1 -> km/h (x180), COOLANT_TEMP 0..1 -> °C (x100).
 */
class Esp32DataSource : CarDataSource {

    @Volatile
    private var latest = CarSnapshot()

    @Volatile
    private var lastRawJson: String? = null

    @Volatile
    private var connected = false

    private val executor = Executors.newSingleThreadExecutor()

    init {
        executor.execute {
            var wasConnected = false
            while (true) {
                try {
                    val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.inputStream.bufferedReader().use { reader ->
                        val text = reader.readText()
                        lastRawJson = text
                        latest = parse(text)
                        if (!wasConnected) Log.i(TAG, "Connected to ESP32 bridge: $text")
                        Log.d(TAG, snapshotLog(latest))
                    }
                    connected = true
                    wasConnected = true
                } catch (e: Exception) {
                    if (wasConnected) Log.w(TAG, "Bridge unreachable (${e.message}); falling back to mock")
                    connected = false
                    wasConnected = false
                }
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
    }

    override fun snapshot(): CarSnapshot = latest

    override fun togglePlayback() {
        latest = latest.copy(
            media = latest.media.copy(isPlaying = !latest.media.isPlaying),
        )
        connected = true
    }

    override fun signalDump(): String? = if (connected) lastRawJson else null

    private fun parse(json: String): CarSnapshot {
        val root = JSONObject(json)
        val sensors = root.optJSONObject("sensors") ?: return CarSnapshot()

        fun value(name: String, default: Double = 0.0): Double =
            sensors.optJSONObject(name)?.optDouble("value", default) ?: default

        fun lit(name: String): Boolean = value(name) >= 0.5

        return CarSnapshot(
            speed = SpeedInfo(kmh = (value("SPEED") * 180.0).roundToInt()),
            climate = ClimateInfo(
                tempC = (value("COOLANT_TEMP") * 100.0).roundToInt(),
                fanLevel = 4,
            ),
            steeringFraction = value("LW1_STEERING_ANGLE").toFloat().coerceIn(0f, 1f),
            highBeam = lit("HIGH_BEAM"),
            turnLeftLamp = lit("TURN_LEFT_LAMP"),
            turnRightLamp = lit("TURN_RIGHT_LAMP"),
            fogLight = lit("FOG_LIGHT"),
            chargeWarning = lit("CHARGE_WARNING"),
            fuelLevel = value("FUEL_LEVEL").toFloat().coerceIn(0f, 1f),
            batteryVoltage = value("BATTERY_VOLTAGE").toFloat(),
        )
    }

    private fun snapshotLog(s: CarSnapshot): String =
        "ESP32 -> speed=${s.speed.kmh} km/h, coolant=${s.climate.tempC}°C, " +
            "steering=${"%.2f".format(s.steeringFraction)}, fuel=${s.fuelLevel}, " +
            "battery=${s.batteryVoltage} V, lamps[left=${s.turnLeftLamp}, " +
            "right=${s.turnRightLamp}, fog=${s.fogLight}, charge=${s.chargeWarning}]"

    private companion object {
        const val TAG = "Esp32DataSource"
        const val ENDPOINT = "http://10.0.2.2:8080/state"
        const val POLL_INTERVAL_MS = 1000L
    }
}
