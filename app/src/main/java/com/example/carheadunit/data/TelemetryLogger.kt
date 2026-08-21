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
 *  1. per-frame signal dumps buffer in memory (cheap, thread-safe)
 *  2. buffers flush to SQLite in batch transactions on a background thread
 *  3. uploads trigger on network events, at boot, and on each flush (all
 *     rate-limited) — batches POST to the Car Telemetry Server and are
 *     deleted only after a 2xx ack; after N consecutive failures the loop
 *     gives up and waits for the next trigger
 *  4. an oldest-first cap keeps the DB bounded during long offline periods
 *
 * Sessions: the server accepts samples only into open sessions, and ends a
 * device's previous session when it starts a new one. The app therefore
 * starts one session per process run (on the first recorded frame) and never
 * ends sessions itself. Rows carry their session_id: past sessions drain
 * before the new session starts (so their tail isn't 409'd), and rows of a
 * 404/409'd session are re-homed into a fresh one rather than dropped.
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

    // Per-frame path: every USB data frame lands here (buffered, flushed in
    // batch transactions; frames arrive far more often than the flush size).
    private val frameBuffer = ArrayList<String>()
    private val frameBufferTs = ArrayList<Long>()

    // Server session for rows recorded by THIS process run; 0 until started.
    // Written only on the upload thread, read by the flush thread when
    // tagging rows — volatile keeps the two in sync.
    @Volatile
    private var currentSessionId = 0L

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
        // Try once at boot in case the network is already up
        scheduleUpload()
        Log.i(TAG, "Telemetry logger started (baseUrl=$BASE_URL)")
    }

    /** Called from the USB reader thread for EVERY data frame (timestamps
     *  captured at parse time). Cheap: buffer add + a batch flush every
     *  [FRAME_FLUSH_EVERY] frames on the background executor. */
    fun frame(ts: Long, payload: String) {
        // Bench mode: print each frame the moment it arrives — no buffering,
        // so the tick-to-logcat path is directly visible.
        if (LOG_ONLY) {
            Log.i(TAG, "Frame[$ts]: $payload")
            return
        }
        synchronized(frameBuffer) {
            frameBuffer.add(payload)
            frameBufferTs.add(ts)
            if (frameBuffer.size >= FRAME_FLUSH_EVERY) {
                val payloads = ArrayList(frameBuffer)
                val stamps = ArrayList(frameBufferTs)
                frameBuffer.clear()
                frameBufferTs.clear()
                executor.execute { flushToDb(payloads, stamps) }
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
                    flushToDb(ArrayList(frameBuffer), ArrayList(frameBufferTs))
                    frameBuffer.clear()
                    frameBufferTs.clear()
                }
            }
            db.close()
        }
    }

    private fun flushToDb(payloads: List<String>, stamps: List<Long>) {
        // Bench mode: skip SQLite and uploads entirely, print frames to logcat.
        // Flip LOG_ONLY to false to restore the store-and-upload pipeline.
        if (LOG_ONLY) {
            for (i in payloads.indices) {
                Log.i(TAG, "Frame[${stamps[i]}]: ${payloads[i]}")
            }
            return
        }
        try {
            db.insertBatch(payloads, stamps, currentSessionId)
            Log.d(TAG, "Stored ${payloads.size} frames")
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
     * One state-machine step, run on the upload thread only:
     *  1. DRAIN past sessions — rows tagged by a previous process run upload
     *     into their still-open session BEFORE a new one starts (starting a
     *     new session ends the old one server-side and would 409 those rows).
     *  2. START — once nothing past is pending and unassigned rows exist,
     *     open this run's session and retag the unassigned rows onto it.
     *  3. UPLOAD — batches of the current session. A current-session batch
     *     sweeps session_id IN (current, 0) so no flush-thread race can
     *     strand an untagged row.
     * A 404/409 marks a session permanently dead: it is never retried; its
     * rows are re-homed into a fresh session (data preserved, queue moves on).
     */
    private fun step(): Step {
        // Phase 1: drain past sessions, oldest first.
        val pastId = db.oldestPastSessionId(currentSessionId)
        if (pastId != 0L) {
            return uploadBatch(pastId)
        }
        // Phase 2: start this run's session once unassigned rows exist.
        if (currentSessionId == 0L) {
            if (!db.hasUnassignedRows()) {
                Log.d(TAG, "Upload: nothing pending")
                return Step.DONE
            }
            val newId = startSession()
            if (newId == 0L) return Step.RETRY
            currentSessionId = newId
            db.assignUnassigned(newId)
        }
        // Phase 3: upload the current session.
        return uploadBatch(currentSessionId)
    }

    private fun uploadBatch(sessionId: Long): Step {
        // Current-session batches sweep stragglers: a row the flush thread
        // tagged 0 after START's reassign belongs to the current session too.
        val batch = db.takeBatch(sessionId, BATCH_SIZE, includeUnassigned = sessionId == currentSessionId)
        if (batch.isEmpty()) return Step.DONE
        val (code, body) = postJson(
            "/telemetry/bulk",
            TelemetryApi.bulkBody(sessionId, batch.map { it.ts to it.payload }),
        )
        return when {
            code in 200..299 -> {
                db.deleteIds(batch.map { it.id })
                Log.i(TAG, "Uploaded ${batch.size} samples (session=$sessionId)")
                Step.OK
            }
            code == 404 || code == 409 -> {
                // Session permanently dead (ended or never existed): retrying
                // can't help and would stall the oldest-first queue. Re-home
                // the rows into a fresh session instead of dropping them.
                Log.w(TAG, "Session $sessionId dead ($code): ${TelemetryApi.errorMessage(body) ?: "no message"}")
                if (sessionId == currentSessionId) currentSessionId = 0L
                if (currentSessionId == 0L) {
                    val newId = startSession()
                    if (newId == 0L) return Step.RETRY
                    currentSessionId = newId
                }
                db.retag(sessionId, currentSessionId)
                Step.OK
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

    /** POST /sessions/start; returns the new session id, or 0 on failure. */
    private fun startSession(): Long {
        val (code, body) = postJson("/sessions/start", TelemetryApi.startBody(DEVICE_ID, VEHICLE_ID))
        if (code !in 200..299) {
            if (code == 401) {
                Log.e(TAG, "Session start rejected: UNAUTHORIZED — check DEVICE_API_TOKEN against the server env")
            } else {
                Log.w(TAG, "Session start failed (code=$code): ${TelemetryApi.errorMessage(body) ?: body}")
            }
            return 0L
        }
        val id = TelemetryApi.parseSessionId(body)
        if (id == null) {
            Log.w(TAG, "Session start returned an unparseable body: $body")
            return 0L
        }
        Log.i(TAG, "Session $id started (device=$DEVICE_ID, vehicle=$VEHICLE_ID)")
        return id
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

    /** One buffered row pulled for upload. */
    private data class PendingRow(val id: Long, val ts: Long, val payload: String)

    private class TelemetryDbHelper(context: Context) :
        SQLiteOpenHelper(context, "telemetry.db", null, 2) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE samples (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "ts INTEGER NOT NULL, " +
                    "session_id INTEGER NOT NULL DEFAULT 0, " +
                    "payload TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX idx_samples_session ON samples(session_id)")
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
        }

        fun insertBatch(payloads: List<String>, stamps: List<Long>, sessionId: Long) {
            writableDatabase.beginTransaction()
            try {
                for (i in payloads.indices) {
                    val v = ContentValues().apply {
                        put("ts", stamps[i])
                        put("session_id", sessionId)
                        put("payload", payloads[i])
                    }
                    writableDatabase.insert("samples", null, v)
                }
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
            prune()
        }

        fun takeBatch(sessionId: Long, limit: Int, includeUnassigned: Boolean): List<PendingRow> {
            val out = ArrayList<PendingRow>(limit)
            val query = if (includeUnassigned) {
                "SELECT _id, ts, payload FROM samples WHERE session_id IN (?, 0) ORDER BY _id ASC LIMIT ?"
            } else {
                "SELECT _id, ts, payload FROM samples WHERE session_id = ? ORDER BY _id ASC LIMIT ?"
            }
            readableDatabase.rawQuery(query, arrayOf(sessionId.toString(), limit.toString())).use { c ->
                while (c.moveToNext()) out.add(PendingRow(c.getLong(0), c.getLong(1), c.getString(2)))
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

        /** Re-homes rows of a dead session onto a live one. */
        fun retag(from: Long, to: Long) =
            chunkedSessionIdUpdate(to, "session_id = ?", arrayOf(from.toString()))

        /** Adopts all untagged rows into the given session. */
        fun assignUnassigned(to: Long) =
            chunkedSessionIdUpdate(to, "session_id = 0", emptyArray())

        /**
         * Rewrites session_id in [RETAG_CHUNK]-row steps. One UPDATE over
         * ~2M rows would hold SQLite's write lock for tens of seconds on a
         * weak head unit, stalling flushes behind it; chunked updates yield
         * the lock between steps so inserts interleave.
         */
        private fun chunkedSessionIdUpdate(to: Long, fromClause: String, fromArgs: Array<String>) {
            val stmt = writableDatabase.compileStatement(
                "UPDATE samples SET session_id = ? WHERE _id IN " +
                    "(SELECT _id FROM samples WHERE $fromClause LIMIT $RETAG_CHUNK)",
            )
            try {
                while (true) {
                    stmt.bindLong(1, to)
                    for (i in fromArgs.indices) stmt.bindString(i + 2, fromArgs[i])
                    // Fewer than a full chunk means the subquery ran dry.
                    if (stmt.executeUpdateDelete() < RETAG_CHUNK) return
                }
            } finally {
                stmt.close()
            }
        }

        /** Oldest session id among rows that are neither untagged nor the
         *  current session; 0 when none. Past-session rows must drain before
         *  a new session starts (the server ends the old one at that point). */
        fun oldestPastSessionId(current: Long): Long =
            readableDatabase.rawQuery(
                "SELECT session_id FROM samples WHERE session_id NOT IN (0, ?) " +
                    "GROUP BY session_id ORDER BY MIN(_id) ASC LIMIT 1",
                arrayOf(current.toString()),
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

        /** Cheap existence check (index-assisted, one row read) — a COUNT(*)
         *  over up to 2M rows would be a full scan on every upload pass. */
        fun hasUnassignedRows(): Boolean =
            readableDatabase.rawQuery(
                "SELECT 1 FROM samples WHERE session_id = 0 LIMIT 1",
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
        // TODO: point at your Car Telemetry Server
        const val BASE_URL = "http://192.168.1.100:3000"
        // Must match the server's DEVICE_API_TOKEN env (exact string match).
        const val DEVICE_API_TOKEN = "CHANGE_ME"
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
        // Rows per step for chunked session_id rewrites (retag/assign).
        const val RETAG_CHUNK = 50_000
    }
}
