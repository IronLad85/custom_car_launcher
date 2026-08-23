package com.example.carheadunit.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TelemetryApiTest {

    // ---- formatTs ----

    @Test
    fun formatTs_emitsIso8601WithOffset() {
        val ms = 1753700401123L
        assertEquals(Instant.ofEpochMilli(ms).toString(), TelemetryApi.formatTs(ms))
        // The server rejects offset-less timestamps; the trailing "Z" is the
        // offset form Instant.toString emits.
        assertTrue(TelemetryApi.formatTs(ms).endsWith("Z"))
    }

    // ---- toSample ----

    @Test
    fun toSample_mapsNumbersWithCorrectTypes() {
        val s = TelemetryApi.toSample(
            """{"ENGINE_RPM":2499.6,"THROTTLE":43.2,"SPEED":42.4,"FUEL_LEVEL":31.0,""" +
                """"ODOMETER":123456.5,"GEAR_VALUE":3.1,"COOLANT_TEMP":91.5,"OIL_TEMP":104.25,""" +
                """"AMBIENT_TEMP":22.75,"OIL_TEMP_ENG":0.0,"MO5_CONSUMPTION":32766.7,"BATTERY_VOLTAGE":13.85}""",
            0L,
        )
        assertEquals(2500, s.getInt("engineRpm"))
        assertEquals(43.2, s.getDouble("throttlePct"), 1e-9)
        assertEquals(42, s.getInt("speedKmh"))
        assertEquals(31.0, s.getDouble("fuelLevelL"), 1e-9)
        assertEquals(123456.5, s.getDouble("odometerKm"), 1e-9)
        assertEquals(3, s.getInt("gearValue"))
        assertEquals(91.5, s.getDouble("coolantTempC"), 1e-9)
        assertEquals(104.25, s.getDouble("oilTempC"), 1e-9)
        assertEquals(22.75, s.getDouble("ambientTempC"), 1e-9)
        assertEquals(0.0, s.getDouble("oilTempEngine"), 1e-9)
        assertEquals(32767, s.getInt("fuelConsumptionUl"))
        assertEquals(13.85, s.getDouble("batteryVoltage"), 1e-9)
    }

    @Test
    fun toSample_coercesBooleansAtHalfBoundary() {
        val s = TelemetryApi.toSample(
            """{"DRIVER_DOOR_OPEN":1.0,"PASS_DOOR_OPEN":0.0,"TURN_LEFT":0.5,""" +
                """"TURN_RIGHT":0.4,"HIGH_BEAM":0.6}""",
            0L,
        )
        assertTrue(s.getBoolean("driverDoorOpen"))
        assertFalse(s.getBoolean("passengerDoorOpen"))
        assertTrue(s.getBoolean("turnLeft")) // boundary: >= 0.5 is true
        assertFalse(s.getBoolean("turnRight"))
        assertTrue(s.getBoolean("highBeam"))
    }

    @Test
    fun toSample_steeringSignCombinesMagnitudeAndSignBit() {
        // App convention (buildSnapshot): sign 1 = right = positive.
        val right = TelemetryApi.toSample("""{"LW1_STEERING_ANGLE":100.0,"LW1_STEER_ANG_SIGN":1.0}""", 0L)
        assertEquals(100.0, right.getDouble("steeringAngleDeg"), 1e-9)
        val left = TelemetryApi.toSample("""{"LW1_STEERING_ANGLE":100.0,"LW1_STEER_ANG_SIGN":0.0}""", 0L)
        assertEquals(-100.0, left.getDouble("steeringAngleDeg"), 1e-9)
        // Sign missing (hand-written payloads only — buildFrameJson appends it).
        val missing = TelemetryApi.toSample("""{"LW1_STEERING_ANGLE":60.0}""", 0L)
        assertEquals(-60.0, missing.getDouble("steeringAngleDeg"), 1e-9)
    }

    @Test
    fun toSample_skipsGearCodeAndUnknownSignals() {
        val s = TelemetryApi.toSample("""{"GEAR":4.0,"GEAR_VALUE":3.0,"SOME_FUTURE_SIGNAL":9.9}""", 0L)
        assertEquals(3, s.getInt("gearValue"))
        assertFalse(s.has("gear"))
        assertFalse(s.has("someFutureSignal"))
        assertEquals(2, s.length()) // ts + gearValue
    }

    @Test
    fun toSample_omitsAbsentAndNullFields() {
        val s = TelemetryApi.toSample("""{"ENGINE_RPM":null,"SPEED":12.0}""", 0L)
        assertFalse(s.has("engineRpm"))
        assertEquals(12, s.getInt("speedKmh"))
    }

    // ---- engineRpm forward-fill ----

    @Test
    fun toSample_forwardFillsEngineRpmWhenKnownAndRunning() {
        // Payloads carry only changed signals: an unchanged engineRpm is
        // re-attached from the logger's last known value (the override).
        val s = TelemetryApi.toSample("""{"SPEED":12.0}""", 0L, engineRpm = 800)
        assertEquals(800, s.getInt("engineRpm"))
    }

    @Test
    fun toSample_omitsEngineRpmWhenEngineOff() {
        val s = TelemetryApi.toSample("""{"SPEED":12.0}""", 0L, engineRpm = 0)
        assertFalse(s.has("engineRpm"))
    }

    @Test
    fun toSample_overrideWinsOverPayload() {
        // The recorded engine state is authoritative; a contradictory payload
        // cannot occur in the logger flow (the override derives from it).
        val s = TelemetryApi.toSample("""{"ENGINE_RPM":2499.6}""", 0L, engineRpm = 0)
        assertFalse(s.has("engineRpm"))
    }

    @Test
    fun parseEngineRpm_readsAndRounds() {
        assertEquals(2500, TelemetryApi.parseEngineRpm("""{"ENGINE_RPM":2499.6}"""))
        assertEquals(0, TelemetryApi.parseEngineRpm("""{"ENGINE_RPM":0.0}"""))
        assertNull(TelemetryApi.parseEngineRpm("""{"SPEED":12.0}"""))
        assertNull(TelemetryApi.parseEngineRpm("{not json"))
    }

    @Test
    fun toSample_unparseablePayloadYieldsTsOnlySample() {
        val ms = 1753700401123L
        val s = TelemetryApi.toSample("{not json", ms)
        assertEquals(TelemetryApi.formatTs(ms), s.getString("ts"))
        assertEquals(1, s.length())
    }

    @Test
    fun toSample_includesIsoTs() {
        val ms = 1753700401123L
        val s = TelemetryApi.toSample("""{"SPEED":12.0}""", ms)
        assertEquals(Instant.ofEpochMilli(ms).toString(), s.getString("ts"))
    }

    // ---- GPS attachment ----

    @Test
    fun toSample_attachesGpsWhenFixPresent() {
        val s = TelemetryApi.toSample(
            """{"SPEED":12.0}""",
            0L,
            lat = 48.137,
            lon = 11.576,
            alt = 519.0,
        )
        assertEquals(48.137, s.getDouble("latitude"), 1e-9)
        assertEquals(11.576, s.getDouble("longitude"), 1e-9)
        assertEquals(519.0, s.getDouble("altitudeM"), 1e-9)
    }

    @Test
    fun toSample_omitsGpsWhenFixAbsentOrPartial() {
        val noFix = TelemetryApi.toSample("""{"SPEED":12.0}""", 0L)
        assertFalse(noFix.has("latitude"))
        assertFalse(noFix.has("longitude"))
        assertFalse(noFix.has("altitudeM"))
        // Lat+lon attach as a pair; a half-fix attaches neither. Altitude
        // can ride along only with the pair (Location always has lat/lon).
        val half = TelemetryApi.toSample("""{"SPEED":12.0}""", 0L, lat = 48.137)
        assertFalse(half.has("latitude"))
        assertFalse(half.has("longitude"))
        val noAlt = TelemetryApi.toSample("""{"SPEED":12.0}""", 0L, lat = 48.137, lon = 11.576)
        assertEquals(48.137, noAlt.getDouble("latitude"), 1e-9)
        assertFalse(noAlt.has("altitudeM"))
    }

    // ---- bulkBody ----

    @Test
    fun bulkBody_carriesDeviceAndSamplesArray() {
        val rows = listOf(
            TelemetryApi.TelemetryRow(1L, """{"SPEED":10.0}""", 0, null, null, null),
            TelemetryApi.TelemetryRow(2L, """{"ENGINE_RPM":1000.0}""", 1000, 48.137, 11.576, 519.0),
        )
        val o = JSONObject(TelemetryApi.bulkBody("headunit-001", "vw-pq", rows))
        assertEquals("headunit-001", o.getString("deviceId"))
        assertEquals("vw-pq", o.getString("vehicleId"))
        assertFalse(o.has("sessionId")) // sessions are server-managed now
        val samples = o.getJSONArray("samples")
        assertEquals(2, samples.length())
        assertEquals(10, samples.getJSONObject(0).getInt("speedKmh"))
        assertFalse(samples.getJSONObject(0).has("latitude"))
        assertFalse(samples.getJSONObject(0).has("engineRpm")) // engine off
        assertEquals(1000, samples.getJSONObject(1).getInt("engineRpm"))
        assertEquals(48.137, samples.getJSONObject(1).getDouble("latitude"), 1e-9)
        assertEquals(519.0, samples.getJSONObject(1).getDouble("altitudeM"), 1e-9)
    }

    @Test
    fun bulkBody_forwardFillsUnchangedEngineRpm() {
        // A row recorded while the engine ran but whose payload lacks
        // ENGINE_RPM (unchanged signal) still carries the forward-filled value.
        val rows = listOf(TelemetryApi.TelemetryRow(1L, """{"SPEED":10.0}""", 800, null, null, null))
        val samples = JSONObject(TelemetryApi.bulkBody("headunit-001", "vw-pq", rows)).getJSONArray("samples")
        assertEquals(800, samples.getJSONObject(0).getInt("engineRpm"))
    }

    // ---- parseBulkAck / errorMessage ----

    @Test
    fun parseBulkAck_readsCountsAndNullableSessionId() {
        val ack = TelemetryApi.parseBulkAck("""{"data":{"sessionId":5,"inserted":2,"dropped":1}}""")
        assertEquals(5L, ack?.sessionId)
        assertEquals(2, ack?.inserted)
        assertEquals(1, ack?.dropped)
        // sessionId null when everything was dropped — not an error.
        val allDropped = TelemetryApi.parseBulkAck("""{"data":{"sessionId":null,"inserted":0,"dropped":3}}""")
        assertNull(allDropped?.sessionId)
        assertEquals(0, allDropped?.inserted)
        assertEquals(3, allDropped?.dropped)
    }

    @Test
    fun parseBulkAck_unparseableYieldsNull() {
        assertNull(TelemetryApi.parseBulkAck("""{"error":"X","message":"nope"}"""))
        assertNull(TelemetryApi.parseBulkAck("garbage"))
    }

    @Test
    fun errorMessage_prefersMessageThenCode() {
        assertEquals("boom", TelemetryApi.errorMessage("""{"error":"X","message":"boom"}"""))
        assertEquals("X", TelemetryApi.errorMessage("""{"error":"X"}"""))
        assertNull(TelemetryApi.errorMessage(""))
        assertNull(TelemetryApi.errorMessage("not json"))
    }
}
