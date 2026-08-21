package com.example.carheadunit.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Pure request-building and payload transforms for the Car Telemetry Server API
 * (http://<HOST>:3000). No Android types and no network I/O, so the host JVM
 * unit tests cover the mapping rules directly.
 *
 * Contract bits this module owns:
 *  - `ts` must be ISO-8601 WITH an offset; epoch-ms rows are converted here.
 *  - Every TelemetrySample field except `ts` is optional; absent fields are
 *    omitted from the JSON rather than sent as null.
 *  - Stored frame payloads are {"SIGNAL_NAME":float,...} maps of the signals
 *    that changed in that frame (names from the firmware registry).
 */
internal object TelemetryApi {

    /** Epoch ms -> ISO-8601 with offset, e.g. 2026-08-21T12:34:56.789Z. */
    fun formatTs(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    fun startBody(deviceId: String, vehicleId: String): String =
        JSONObject().put("deviceId", deviceId).put("vehicleId", vehicleId).toString()

    /** data.sessionId from a /sessions/start 201 body; null when not parseable. */
    fun parseSessionId(body: String): Long? = runCatching {
        JSONObject(body).getJSONObject("data").getLong("sessionId")
    }.getOrNull()

    /** One buffered row headed for /telemetry/bulk (GPS captured at recording time). */
    internal data class TelemetryRow(
        val ts: Long,
        val payload: String,
        val lat: Double?,
        val lon: Double?,
        val alt: Double?,
    )

    /** /telemetry/bulk body for one session's rows, oldest first. */
    fun bulkBody(sessionId: Long, rows: List<TelemetryRow>): String {
        val samples = JSONArray()
        for (r in rows) samples.put(toSample(r.payload, r.ts, r.lat, r.lon, r.alt))
        return JSONObject().put("sessionId", sessionId).put("samples", samples).toString()
    }

    /** Human-readable reason from an error body ({"error","message"}); null when absent. */
    fun errorMessage(body: String): String? = runCatching {
        val o = JSONObject(body)
        if (o.has("message") && !o.isNull("message")) o.getString("message")
        else if (o.has("error") && !o.isNull("error")) o.getString("error")
        else null
    }.getOrNull()

    /** One stored frame payload -> one TelemetrySample. Unknown signals are
     *  ignored, absent fields are omitted, and non-finite numbers are dropped
     *  (org.json throws on NaN/Infinity). GPS attaches when a fresh fix was
     *  captured at recording time (lat+lon together, altitude on its own). */
    fun toSample(
        payload: String,
        tsMs: Long,
        lat: Double? = null,
        lon: Double? = null,
        alt: Double? = null,
    ): JSONObject {
        val out = JSONObject().put("ts", formatTs(tsMs))
        val src = runCatching { JSONObject(payload) }.getOrNull() ?: return out
        fun num(signal: String, field: String, asInt: Boolean = false) {
            val v = src.optDouble(signal, Double.NaN)
            if (!v.isFinite()) return
            out.put(field, if (asInt) Math.round(v).toInt() else v)
        }
        fun lit(signal: String, field: String) {
            val v = src.optDouble(signal, Double.NaN)
            if (!v.isFinite()) return
            out.put(field, v >= 0.5)
        }
        num("ENGINE_RPM", "engineRpm", asInt = true)
        num("THROTTLE", "throttlePct")
        num("SPEED", "speedKmh", asInt = true)
        num("FUEL_LEVEL", "fuelLevelL")
        num("ODOMETER", "odometerKm")
        num("GEAR_VALUE", "gearValue", asInt = true)
        num("COOLANT_TEMP", "coolantTempC")
        num("OIL_TEMP", "oilTempC")
        num("AMBIENT_TEMP", "ambientTempC")
        num("OIL_TEMP_ENG", "oilTempEngine")
        num("MO5_CONSUMPTION", "fuelConsumptionUl", asInt = true)
        num("BATTERY_VOLTAGE", "batteryVoltage")
        // Steering: |angle| plus a separate sign bit (1 = right = positive,
        // same convention as buildSnapshot). Currently excluded at the source
        // (EXCLUDED_SIGNALS) — the mapping stays so re-enabling is one line.
        val angle = src.optDouble("LW1_STEERING_ANGLE", Double.NaN)
        if (angle.isFinite()) {
            val sign = src.optDouble("LW1_STEER_ANG_SIGN", 0.0)
            out.put("steeringAngleDeg", if (sign >= 0.5) angle else -angle)
        }
        // GEAR (the 0..15 raw code) is skipped on purpose: the server's
        // `gear` field wants a ≤2-char string and there's no code table in
        // the app; GEAR_VALUE carries the numeric gear instead.
        // Lamp/indicator/brake booleans are currently excluded at the source
        // (EXCLUDED_SIGNALS); the mappings stay so re-enabling is one line.
        lit("TURN_LEFT", "turnLeft")
        lit("TURN_RIGHT", "turnRight")
        lit("DRIVER_DOOR_OPEN", "driverDoorOpen")
        lit("PASS_DOOR_OPEN", "passengerDoorOpen")
        lit("DOOR_REAR_LEFT_OPEN", "rearLeftDoorOpen")
        lit("DOOR_REAR_RIGHT_OPEN", "rearRightDoorOpen")
        lit("HOOD_OPEN", "hoodOpen")
        lit("TRUNK_OPEN", "trunkOpen")
        lit("TURN_LEFT_LAMP", "turnLeftLamp")
        lit("TURN_RIGHT_LAMP", "turnRightLamp")
        lit("HAZARD_MODE", "hazardMode")
        lit("LOW_BEAM", "lowBeam")
        lit("HIGH_BEAM", "highBeam")
        lit("FOG_LIGHT", "fogLight")
        lit("REVERSE_LIGHT", "reverseLight")
        lit("BRAKE_LIGHT", "brakeLight")
        lit("CHARGE_WARNING", "chargeWarning")
        if (lat != null && lon != null) {
            out.put("latitude", lat)
            out.put("longitude", lon)
        }
        alt?.let { out.put("altitudeM", it) }
        return out
    }
}
