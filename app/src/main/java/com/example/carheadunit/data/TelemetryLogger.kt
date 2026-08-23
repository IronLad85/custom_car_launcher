package com.example.carheadunit.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Edge telemetry pipeline for the ESP32 CAN data:
 *  1. per-frame signal dumps + the current GPS fix buffer in memory (cheap,
 *     thread-safe; the fix is captured at recording time so offline uploads
 *     never stamp old rows with a newer position)
 *  2. buffers flush to SQLite in batch transactions on a background thread
 *  3. uploads trigger on network events, at boot, and on each flush (all
 *     rate-limited) — batches POST to the Car Telemetry Server and are
 *     deleted only after a 2xx ack; after N consecutive failures the loop
 *     gives up and waits for the next trigger
 *  4. an oldest-first cap keeps the DB bounded during long offline periods
 *
 * Sessions are server-side only: the server opens a session at the first
 * engineRpm > 0 sample, closes it once data gaps exceed 5 min, and opens a
 * new one at the next engineRpm > 0 sample. The app never starts, tracks, or
 * ends sessions — it just keeps pushing, and every bulk body carries
 * deviceId/vehicleId. Payloads carry only changed signals, so the last known
 * ENGINE_RPM is forward-filled into every sample while non-zero (a sample
 * without engineRpm reads as engine-off and is dropped server-side — counted
 * in `dropped` — when no session is open). Dropped samples are not retried;
 * the ack's inserted/dropped counts are only logged.
 *
 * Flushes and uploads run on separate single threads: a long upload retry
 * loop can never block buffered frames from reaching SQLite.
 */
class TelemetryLogger(context: Context) {

    private val appContext = context.applicationContext
    private val db = TelemetryDbHelper(appContext)
    private val executor: Executor = Executors.newSingleThreadExecutor()

    // Separate thread for uploads so a retry loop never delays flushes
    // (otherwise buffered frames would pile up in RAM).
    private val uploadExecutor = Executors.newSingleThreadExecutor()

    // GPS fixes attached to rows at recording time (10 s refresh cadence).
    private val gps = GpsProvider(appContext)

    // Per-frame path: every USB data frame lands here (buffered, flushed in
    // batch transactions; frames arrive far more often than the flush size).
    private val frameBuffer = ArrayList<BufferedFrame>()

    // Last known ENGINE_RPM for forward-fill: payloads carry only signals
    // that changed, and a sample without engineRpm reads as engine-off to the
    // server. 0 = engine off / unknown; only touched on the USB reader thread
    // (frame() is the sole writer and reader).
    private var lastEngineRpm = 0

    @Volatile
    private var lastUploadAttemptAt = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available — attempting upload")
            scheduleUpload()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                scheduleUpload()
            }
        }
    }

    init {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(networkCallback)
        gps.start()
        // Try once at boot in case the network is already up
        scheduleUpload()
        Log.i(TAG, "Telemetry logger started (baseUrl=$BASE_URL)")
    }

    /** Re-attempts GPS start after the runtime location permission result
     *  lands (the init attempt fails while the dialog is still pending);
     *  no-op if tracking is already running. */
    fun onLocationPermissionGranted() {
        gps.start()
    }

    /** Called from the USB reader thread for EVERY data frame (timestamps
     *  captured at parse time; the GPS fix is captured here too so it matches
     *  the frame's recording moment). Cheap: buffer add + a batch flush every
     *  [FRAME_FLUSH_EVERY] frames on the background executor. */
    fun frame(ts: Long, payload: String) {
        // Bench mode: print each frame the moment it arrives — no buffering,
        // so the tick-to-logcat path is directly visible.
        if (LOG_ONLY) {
            Log.i(TAG, "Frame[$ts]: $payload")
            return
        }
        // Engine state captured at recording time (like the GPS fix): the
        // frame's own ENGINE_RPM updates the running value; a frame without
        // it keeps the last known value while it's > 0. Stored per row so
        // offline uploads re-attach the state the sample was recorded with.
        TelemetryApi.parseEngineRpm(payload)?.let { lastEngineRpm = it }
        val engineRpm = if (lastEngineRpm > 0) lastEngineRpm else 0
        val fix = gps.currentFix()
        val frame = BufferedFrame(
            ts,
            payload,
            engineRpm,
            fix?.latitude,
            fix?.longitude,
            fix?.takeIf { it.hasAltitude() }?.altitude,
        )
        synchronized(frameBuffer) {
            frameBuffer.add(frame)
            if (frameBuffer.size >= FRAME_FLUSH_EVERY) {
                val batch = ArrayList(frameBuffer)
                frameBuffer.clear()
                executor.execute { flushToDb(batch) }
            }
        }
    }

    /** Flushes whatever is buffered and closes the DB (single-use logger).
     *  Ordering matters: uploads must stop before the DB closes — both run on
     *  their own threads, so the upload executor is shut down and awaited from
     *  inside the final flush task. */
    fun close() {
        executor.execute {
            uploadExecutor.shutdownNow()
            runCatching { uploadExecutor.awaitTermination(2, TimeUnit.SECONDS) }
            synchronized(frameBuffer) {
                if (frameBuffer.isNotEmpty()) {
                    flushToDb(ArrayList(frameBuffer))
                    frameBuffer.clear()
                }
            }
            gps.stop()
            db.close()
        }
    }

    private fun flushToDb(frames: List<BufferedFrame>) {
        // Bench mode: skip SQLite and uploads entirely, print frames to logcat.
        // Flip LOG_ONLY to false to restore the store-and-upload pipeline.
        if (LOG_ONLY) {
            for (f in frames) {
                Log.i(TAG, "Frame[${f.ts}]: ${f.payload}")
            }
            return
        }
        try {
            db.insertBatch(frames)
            Log.d(TAG, "Stored ${frames.size} frames")
            // A flush is also a retry trigger: if rows are pending and the
            // last attempt is old enough (rate-limited in scheduleUpload),
            // try an upload — recovers without waiting for a network event.
            scheduleUpload()
        } catch (e: Exception) {
            Log.w(TAG, "DB insert failed", e)
        }
    }

    private fun scheduleUpload() {
        if (LOG_ONLY) return
        val now = System.currentTimeMillis()
        // Basic rate-limit: at most one upload pass per interval regardless
        // of callbacks and flush triggers.
        if (now - lastUploadAttemptAt < MIN_UPLOAD_INTERVAL_MS) return
        lastUploadAttemptAt = now
        // The executor may already be shut down (close()); the final flush
        // still calls scheduleUpload from flushToDb.
        runCatching { uploadExecutor.execute { uploadLoop() } }
    }

    private enum class Step { OK, DONE, RETRY }

    private fun uploadLoop() {
        var backoff = 5_000L
        var consecutiveFailures = 0
        while (true) {
            when (step()) {
                Step.OK -> {
                    consecutiveFailures = 0
                    backoff = 5_000L
                }
                Step.DONE -> return
                Step.RETRY -> {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_UPLOAD_FAILURES) {
                        // Give up: rows stay in SQLite; the next network event or
                        // flush re-triggers the upload. Also stops pointless
                        // spinning on dead networks / wrong endpoints.
                        Log.w(TAG, "Upload paused after $consecutiveFailures failures — waiting for the next trigger")
                        return
                    }
                    Log.w(TAG, "Upload failed — retrying in ${backoff / 1000}s")
                    try {
                        Thread.sleep(backoff)
                    } catch (_: InterruptedException) {
                        return
                    }
                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
    }

    /**
     * One state-machine step, run on the upload thread only: POST the oldest
     * pending rows to /telemetry/bulk. Sessions are server-managed (opened at
     * the first engineRpm > 0 sample, closed after a > 5 min data gap), so
     * there is no start call, no session id, and no 404/409 recovery — a 2xx
     * ack deletes the batch even when `dropped` > 0 (dropped samples would
     * just be dropped again on retry).
     */
    private fun step(): Step {
        if (!db.hasPendingRows()) {
            Log.d(TAG, "Upload: nothing pending")
            return Step.DONE
        }
        return uploadBatch()
    }

    private fun uploadBatch(): Step {
        val batch = db.takeBatch(BATCH_SIZE)
        if (batch.isEmpty()) return Step.DONE
        val (code, body) = postJson(
            "/telemetry/bulk",
            TelemetryApi.bulkBody(
                DEVICE_ID,
                VEHICLE_ID,
                batch.map { TelemetryApi.TelemetryRow(it.ts, it.payload, it.engineRpm, it.lat, it.lon, it.alt) },
            ),
        )
        return when {
            code in 200..299 -> {
                db.deleteIds(batch.map { it.id })
                val ack = TelemetryApi.parseBulkAck(body)
                if (ack != null) {
                    // sessionId is null when everything was dropped — engine-off
                    // samples with no open session; not an error, not retried.
                    Log.i(TAG, "Uploaded ${ack.inserted} samples, ${ack.dropped} dropped (session=${ack.sessionId})")
                } else {
                    Log.i(TAG, "Uploaded ${batch.size} samples")
                }
                Step.OK
            }
            code == 400 -> {
                // Bad body — a request-building bug in this app; backoff won't
                // fix it, but retrying on the next trigger lets a fix pick up
                // without an app restart.
                Log.e(TAG, "Upload rejected: BAD REQUEST — check the bulk body: ${TelemetryApi.errorMessage(body) ?: body}")
                Step.RETRY
            }
            code == 401 -> {
                // Token/config error — backoff won't fix it, but retrying on
                // the next trigger lets a server-side fix pick up without an
                // app restart.
                Log.e(TAG, "Upload rejected: UNAUTHORIZED — check DEVICE_API_TOKEN against the server env")
                Step.RETRY
            }
            else -> {
                Log.w(TAG, "Upload error (code=$code): ${TelemetryApi.errorMessage(body) ?: body}")
                Step.RETRY
            }
        }
    }

    /** POSTs a JSON body to BASE_URL + path. Returns (httpCode, responseBody);
     *  code -1 = network-level failure (no HTTP response). */
    private fun postJson(path: String, body: String): Pair<Int, String> =
        try {
            val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $DEVICE_API_TOKEN")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            conn.disconnect()
            code to responseBody
        } catch (e: Exception) {
            Log.w(TAG, "POST $path failed: ${e.message}")
            -1 to ""
        }

    /** One frame held in RAM before its SQLite flush, with the GPS fix and
     *  forward-filled engineRpm (0 = engine off) that were current at capture
     *  time. */
    private data class BufferedFrame(
        val ts: Long,
        val payload: String,
        val engineRpm: Int,
        val lat: Double?,
        val lon: Double?,
        val alt: Double?,
    )

    /** One buffered row pulled for upload. */
    private data class PendingRow(
        val id: Long,
        val ts: Long,
        val payload: String,
        val engineRpm: Int,
        val lat: Double?,
        val lon: Double?,
        val alt: Double?,
    )

    private class TelemetryDbHelper(context: Context) :
        SQLiteOpenHelper(context, "telemetry.db", null, 4) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE samples (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "ts INTEGER NOT NULL, " +
                    "engine_rpm INTEGER NOT NULL DEFAULT 0, " +
                    "latitude REAL, " +
                    "longitude REAL, " +
                    "altitude_m REAL, " +
                    "payload TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE samples ADD COLUMN session_id INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX idx_samples_session ON samples(session_id)")
                // Legacy rows predate the session model and targeted the old
                // placeholder endpoint. Left at session_id=0 they'd be swept
                // into the first new session and corrupt its trip stats.
                db.execSQL("DELETE FROM samples")
            }
            if (oldVersion < 3) {
                // GPS arrives with the row; null columns = no fresh fix.
                db.execSQL("ALTER TABLE samples ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE samples ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE samples ADD COLUMN altitude_m REAL")
            }
            if (oldVersion < 4) {
                // Session model removed (the server manages sessions on its
                // own): drop session_id and carry the forward-filled
                // engineRpm per row instead. Rebuild preserves rows; old
                // rows get engine_rpm=0 (unknown engine state, treated as
                // engine-off by the server).
                db.execSQL("ALTER TABLE samples RENAME TO samples_old")
                db.execSQL(
                    "CREATE TABLE samples (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "ts INTEGER NOT NULL, " +
                        "engine_rpm INTEGER NOT NULL DEFAULT 0, " +
                        "latitude REAL, " +
                        "longitude REAL, " +
                        "altitude_m REAL, " +
                        "payload TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO samples (_id, ts, engine_rpm, latitude, longitude, altitude_m, payload) " +
                        "SELECT _id, ts, 0, latitude, longitude, altitude_m, payload FROM samples_old",
                )
                db.execSQL("DROP TABLE samples_old")
            }
        }

        fun insertBatch(frames: List<BufferedFrame>) {
            writableDatabase.beginTransaction()
            try {
                for (f in frames) {
                    val v = ContentValues().apply {
                        put("ts", f.ts)
                        put("engine_rpm", f.engineRpm)
                        put("payload", f.payload)
                        f.lat?.let { put("latitude", it) }
                        f.lon?.let { put("longitude", it) }
                        f.alt?.let { put("altitude_m", it) }
                    }
                    writableDatabase.insert("samples", null, v)
                }
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
            prune()
        }

        fun takeBatch(limit: Int): List<PendingRow> {
            val out = ArrayList<PendingRow>(limit)
            readableDatabase.rawQuery(
                "SELECT _id, ts, payload, engine_rpm, latitude, longitude, altitude_m FROM samples " +
                    "ORDER BY _id ASC LIMIT ?",
                arrayOf(limit.toString()),
            ).use { c ->
                fun nullableDouble(i: Int): Double? = if (c.isNull(i)) null else c.getDouble(i)
                while (c.moveToNext()) {
                    out.add(
                        PendingRow(
                            c.getLong(0),
                            c.getLong(1),
                            c.getString(2),
                            c.getInt(3),
                            nullableDouble(4),
                            nullableDouble(5),
                            nullableDouble(6),
                        ),
                    )
                }
            }
            return out
        }

        fun deleteIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            val placeholders = ids.joinToString(",") { "?" }
            writableDatabase.execSQL(
                "DELETE FROM samples WHERE _id IN ($placeholders)",
                ids.map { it.toString() }.toTypedArray(),
            )
        }

        /** Cheap existence check (index-assisted on the PK, one row read) — a
         *  COUNT(*) over up to 2M rows would be a full scan on every upload
         *  pass. */
        fun hasPendingRows(): Boolean =
            readableDatabase.rawQuery(
                "SELECT 1 FROM samples LIMIT 1",
                null,
            ).use { it.moveToFirst() }

        /** Keep the table bounded: retain only the newest rows. */
        private fun prune() {
            writableDatabase.execSQL(
                "DELETE FROM samples WHERE _id NOT IN " +
                    "(SELECT _id FROM samples ORDER BY _id DESC LIMIT $MAX_ROWS)",
            )
        }
    }

    private companion object {
        const val TAG = "TelemetryLogger"
        // Normal mode: frames buffer to SQLite and upload on network events.
        // Flip to true for bench mode (logcat only, no SQLite, no HTTP).
        const val LOG_ONLY = false
        // Car Telemetry Server base URL (no path prefix).
        const val BASE_URL = "https://car-data-server.techstark.in"
        // Must match the server's DEVICE_API_TOKEN env (exact string match).
        const val DEVICE_API_TOKEN = "c236a4e1e5bee1e2d229b627c8e51158000844543d0873811ff0933dac9be7a6"
        // Same identifier the old /sessions/start call sent — every bulk push
        // carries it so the server can group samples into sessions per device.
        const val DEVICE_ID = "headunit-001"
        // TODO: whatever identifies the car
        const val VEHICLE_ID = "vw-pq"
        // Frames arrive at up to ~50/s (firmware 20 ms flush) — batch larger
        // so SQLite sees one small transaction every few seconds, not 50/s.
        const val FRAME_FLUSH_EVERY = 200
        const val BATCH_SIZE = 50
        const val MIN_UPLOAD_INTERVAL_MS = 30_000L
        const val MAX_BACKOFF_MS = 300_000L
        // Consecutive failed POSTs before the upload loop gives up and waits
        // for the next trigger (network event or flush).
        const val MAX_UPLOAD_FAILURES = 5
        // 2M rows ≈ several days of per-frame data at driving rates; uploads
        // on daily WiFi drain it long before the cap. Tune if needed.
        const val MAX_ROWS = 2_000_000
    }
}
