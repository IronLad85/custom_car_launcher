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

/**
 * Edge telemetry pipeline for the ESP32 CAN data:
 *  1. [sample] buffers signal dumps in memory (cheap, main-thread safe)
 *  2. buffers flush to SQLite in batch transactions on a background thread
 *  3. a network callback triggers [uploadPending] — batches POST to the
 *     server and are deleted only after a 2xx ack
 *  4. an oldest-first cap keeps the DB bounded during long offline periods
 *
 * Steady-state cost: one small disk write per flush and one HTTP POST per
 * upload window — nothing per message. No polling: network events come from
 * ConnectivityManager callbacks.
 */
class TelemetryLogger(context: Context) {

    private val appContext = context.applicationContext
    private val db = TelemetryDbHelper(appContext)
    private val executor: Executor = Executors.newSingleThreadExecutor()

    private val buffer = ArrayList<String>()
    private val bufferTs = ArrayList<Long>()

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
        Log.i(TAG, "Telemetry logger started (endpoint=$ENDPOINT)")
    }

    /** Called from the tick with a serialized signal dump; cheap and main-thread safe. */
    fun sample(payload: String) {
        synchronized(buffer) {
            buffer.add(payload)
            bufferTs.add(System.currentTimeMillis())
            if (buffer.size >= FLUSH_EVERY_SAMPLES) {
                val payloads = ArrayList(buffer)
                val stamps = ArrayList(bufferTs)
                buffer.clear()
                bufferTs.clear()
                executor.execute { flushToDb(payloads, stamps) }
            }
        }
    }

    /** Flushes whatever is buffered and closes the DB (single-use logger). */
    fun close() {
        executor.execute {
            synchronized(buffer) {
                if (buffer.isNotEmpty()) {
                    flushToDb(ArrayList(buffer), ArrayList(bufferTs))
                    buffer.clear()
                    bufferTs.clear()
                }
            }
            db.close()
        }
    }

    private fun flushToDb(payloads: List<String>, stamps: List<Long>) {
        try {
            db.insertBatch(payloads, stamps)
            Log.d(TAG, "Stored ${payloads.size} samples (pending=${db.pendingCount()})")
        } catch (e: Exception) {
            Log.w(TAG, "DB insert failed", e)
        }
    }

    private fun scheduleUpload() {
        val now = System.currentTimeMillis()
        // Basic rate-limit: at most one upload pass per minute regardless of callbacks
        if (now - lastUploadAttemptAt < MIN_UPLOAD_INTERVAL_MS) return
        lastUploadAttemptAt = now
        executor.execute { uploadLoop() }
    }

    private fun uploadLoop() {
        var backoff = 5_000L
        while (true) {
            val pending = db.pendingCount()
            if (pending == 0) {
                Log.d(TAG, "Upload: nothing pending")
                return
            }
            val batch = db.takeBatch(BATCH_SIZE)
            if (batch.isEmpty()) return
            val ok = post(batch)
            if (ok) {
                db.deleteIds(batch.map { it.first })
                Log.i(TAG, "Uploaded ${batch.size} samples (remaining=${db.pendingCount()})")
            } else {
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

    private fun post(batch: List<Pair<Long, String>>): Boolean =
        try {
            val sb = StringBuilder("""{"device":"$DEVICE_ID","samples":[""")
            batch.forEachIndexed { i, (ts, payload) ->
                if (i > 0) sb.append(',')
                sb.append("""{"t":$ts,"d":$payload}""")
            }
            sb.append("]}")
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(sb.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Upload error: ${e.message}")
            false
        }

    private class TelemetryDbHelper(context: Context) :
        SQLiteOpenHelper(context, "telemetry.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE samples (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "ts INTEGER NOT NULL, " +
                    "payload TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

        fun insertBatch(payloads: List<String>, stamps: List<Long>) {
            writableDatabase.beginTransaction()
            try {
                for (i in payloads.indices) {
                    val v = ContentValues().apply {
                        put("ts", stamps[i])
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

        fun takeBatch(limit: Int): List<Pair<Long, String>> {
            val out = ArrayList<Pair<Long, String>>(limit)
            readableDatabase.rawQuery(
                "SELECT _id, payload FROM samples ORDER BY _id ASC LIMIT ?",
                arrayOf(limit.toString()),
            ).use { c ->
                while (c.moveToNext()) out.add(c.getLong(0) to c.getString(1))
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

        fun pendingCount(): Int =
            readableDatabase.rawQuery("SELECT COUNT(*) FROM samples", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }

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
        // TODO: replace with your server endpoint
        const val ENDPOINT = "https://telemetry.example.com/api/ingest"
        const val DEVICE_ID = "headunit-001"
        const val FLUSH_EVERY_SAMPLES = 10
        const val BATCH_SIZE = 50
        const val MIN_UPLOAD_INTERVAL_MS = 30_000L
        const val MAX_BACKOFF_MS = 300_000L
        const val MAX_ROWS = 200_000
    }
}
