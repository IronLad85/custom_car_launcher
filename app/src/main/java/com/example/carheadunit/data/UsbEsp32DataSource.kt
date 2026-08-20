package com.example.carheadunit.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** USB link health shown in the dock status chip. */
enum class UsbLinkState(val label: String) {
    OFFLINE("ESP32 offline"),
    RETRYING("ESP32 retrying…"),
    CONNECTING("ESP32 connecting…"),
    CONNECTED("ESP32 connected"),
    STREAMING("ESP32 streaming"),
    DATA("ESP32 live"),
    FAILED("ESP32 failed"),
}

/**
 * Live telemetry from the CAN Sniffer (ESP32-S3) over USB CDC-ACM.
 * Protocol (docs/usb_protocol.md): raw command bytes out (0x53 start, 0x50
 * pause), raw ACKs + CBOR in. Registry maps index -> name/unit/scale/offset;
 * data messages carry final values — the firmware applies scale/offset.
 *
 * While no USB device is attached the source reports the static default
 * snapshot (the dock shows "ESP32 offline").
 *
 * Reconnect policy: failed opens (and device scans while absent) retry with
 * exponential delays starting at 10s, capped at 60s, up to 5 attempts per
 * episode; attach/permission events and successful opens reset the episode.
 */
class UsbEsp32DataSource(private val context: Context) : CarDataSource {

    @Volatile
    private var connected = false

    @Volatile
    private var running = false

    private val parser = CborParser()
    private val signalMeta = HashMap<Long, SignalMeta>()
    private val signalValues = HashMap<String, Float>()

    private var registryCount = 0

    private var connection: UsbDeviceConnection? = null
    private var usbIface: UsbInterface? = null
    private var inEp: UsbEndpoint? = null
    private var outEp: UsbEndpoint? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val retryExecutor = Executors.newSingleThreadScheduledExecutor()

    // Reconnect episode state (guarded by scheduleReconnect/resetRetry)
    private var retryAttempt = 0
    private var retryTask: ScheduledFuture<*>? = null

    // Link health for the dock status chip
    private val _status = MutableStateFlow(UsbLinkState.OFFLINE)
    val status: StateFlow<UsbLinkState> = _status.asStateFlow()

    // One-shot flags gating status progression (reset on disconnect)
    private var registrySeen = false
    private var dataSeen = false

    // Registry bookkeeping: the device sends the expected total (key 2) with
    // every registry entry. If entries were lost in transit (TX contention on
    // the device drops packets), a delayed check asks for a re-send.
    private var registryTotal = 0
    private var registryRequested = false

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB permission granted=$granted")
                    if (granted) {
                        _status.value = UsbLinkState.CONNECTING
                        resetRetry()
                        connect()
                    } else {
                        Log.w(TAG, "USB permission denied — retries stopped")
                        _status.value = UsbLinkState.FAILED
                        resetRetry()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.i(TAG, "USB device attached")
                    _status.value = UsbLinkState.CONNECTING
                    resetRetry()
                    connect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB device detached")
                    disconnect()
                    resetRetry()
                    scheduleReconnect()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        connect()
    }

    // ---- lifecycle ----

    /** Called when the app goes to the background: pauses the stream (0x50). */
    fun pause() {
        if (connected) executor.execute { writeCommand(CMD_PAUSE) }
    }

    /** Called when the app returns to the foreground: resumes (0x53, snapshot refresh). */
    fun resume() {
        if (connected) executor.execute { writeCommand(CMD_START) }
    }

    /** Release everything; the data source is single-use. */
    fun close() {
        disconnect()
        runCatching { context.unregisterReceiver(receiver) }
        retryExecutor.shutdownNow()
    }

    // ---- CarDataSource ----

    override fun snapshot(): CarSnapshot {
        if (!connected) return CarSnapshot() // offline: static defaults, dock shows the link state
        val values: Map<String, Float> = synchronized(signalValues) { HashMap(signalValues) }
        return buildSnapshot(values)
    }

    override fun signalDump(): String? {
        if (!connected) return null
        val values: Map<String, Float> = synchronized(signalValues) { HashMap(signalValues) }
        val sb = StringBuilder("{")
        values.entries.forEachIndexed { i, (name, value) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(name).append("\":").append(value)
        }
        sb.append("}")
        return sb.toString()
    }

    // ---- connection ----

    private fun connect() {
        val dev = usbManager.deviceList.values.firstOrNull {
            it.vendorId == VID && (it.productId == PID || it.productId == PID_ESP_TINYUSB_AUTO)
        } ?: run {
            Log.d(TAG, "No CAN Sniffer device present")
            scheduleReconnect()
            return
        }
        if (!usbManager.hasPermission(dev)) {
            Log.i(TAG, "Requesting USB permission for ${dev.deviceName}")
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_MUTABLE,
            )
            usbManager.requestPermission(dev, pi)
            return
        }
        executor.execute { openDevice(dev) }
    }

    /**
     * The TinyUSB default descriptor exposes two interfaces: 0 = CDC control
     * (a single interrupt endpoint), 1 = CDC data (the bulk pair). The data
     * interface lists its endpoints OUT first, then IN — so both the interface
     * index and the endpoint order must be resolved dynamically.
     */
    private fun findDataInterface(dev: UsbDevice): UsbInterface? {
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                iface.endpointCount == 2
            ) {
                return iface
            }
        }
        return null
    }

    private fun openDevice(dev: UsbDevice) {
        try {
            val iface = findDataInterface(dev)
            if (iface == null) {
                Log.w(TAG, "No CDC data interface on ${dev.deviceName}")
                scheduleReconnect()
                return
            }
            val conn = usbManager.openDevice(dev) ?: return
            if (!conn.claimInterface(iface, true)) {
                conn.close()
                return
            }
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                when (ep.direction) {
                    UsbConstants.USB_DIR_IN -> inEp = ep
                    UsbConstants.USB_DIR_OUT -> outEp = ep
                }
            }
            if (inEp == null || outEp == null) {
                Log.w(TAG, "CDC data interface missing bulk IN/OUT endpoints")
                conn.close()
                scheduleReconnect()
                return
            }
            // Assert DTR (SET_CONTROL_LINE_STATE) — the device stays silent until then
            conn.controlTransfer(0x21, 0x22, 0x0001, 0, null, 0, 100)
            connection = conn
            usbIface = iface
            connected = true
            resetRetry()
            _status.value = UsbLinkState.CONNECTED
            Log.i(TAG, "USB connected: ${dev.deviceName}")
            writeCommand(CMD_START) // -> device answers 0xA1 + registry + snapshot + stream
            runReader()
        } catch (e: Exception) {
            Log.w(TAG, "USB open failed", e)
            disconnect()
            scheduleReconnect()
        }
    }

    private fun runReader() {
        val conn = connection ?: return
        val ep = inEp ?: return
        val buf = ByteArray(256)
        running = true
        var consecutiveFailures = 0
        while (running && connected) {
            val n = conn.bulkTransfer(ep, buf, buf.size, 200)
            when {
                n > 0 -> {
                    consecutiveFailures = 0
                    parser.feed(buf, n)
                    while (true) {
                        val item = parser.nextItem() ?: break
                        handleItem(item)
                    }
                }
                // Not necessarily a device reset/stall: several host kernels
                // (e.g. OnePlus) return -1 on a read timeout instead of 0.
                // A real reset fails immediately and repeatedly; a quiet link
                // fails once per 200 ms poll (the firmware heartbeats at
                // 500 ms). Require a run of failures before reconnecting.
                n < 0 -> {
                    consecutiveFailures++
                    if (consecutiveFailures >= READ_FAILURE_LIMIT) {
                        Log.w(TAG, "USB read failed $consecutiveFailures times — reconnecting")
                        disconnect()
                        scheduleReconnect()
                        break
                    }
                }
            }
        }
        Log.i(TAG, "USB reader stopped")
    }

    private fun disconnect() {
        running = false
        runCatching {
            val iface = usbIface
            if (iface != null) connection?.releaseInterface(iface)
        }
        runCatching { connection?.close() }
        connection = null
        usbIface = null
        inEp = null
        outEp = null
        if (connected) {
            Log.i(TAG, "USB disconnected")
        }
        connected = false
        _status.value = UsbLinkState.OFFLINE
        registrySeen = false
        dataSeen = false
        registryTotal = 0
        registryRequested = false
        parser.reset()
        signalMeta.clear()
        synchronized(signalValues) { signalValues.clear() }
    }

    private fun writeCommand(cmd: Int) {
        val conn = connection ?: return
        val ep = outEp ?: return
        conn.bulkTransfer(ep, byteArrayOf(cmd.toByte()), 1, 100)
    }

    // ---- reconnect ----

    /**
     * Schedules the next reconnect attempt with exponential delays: starts at
     * RETRY_BASE_DELAY_MS and doubles per failure, capped at RETRY_MAX_DELAY_MS,
     * at most MAX_RETRY_ATTEMPTS per episode. Any attach/permission event or a
     * successful open resets the episode.
     */
    private fun scheduleReconnect() {
        synchronized(this) {
            retryTask?.cancel(false)
            if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
                Log.i(TAG, "USB reconnect gave up after $MAX_RETRY_ATTEMPTS attempts")
                _status.value = UsbLinkState.FAILED
                return
            }
            if (retryAttempt == 0) _status.value = UsbLinkState.RETRYING
            val delay = minOf(RETRY_BASE_DELAY_MS * (1L shl retryAttempt), RETRY_MAX_DELAY_MS)
            retryAttempt++
            Log.i(TAG, "USB reconnect attempt $retryAttempt/$MAX_RETRY_ATTEMPTS in ${delay / 1000}s")
            retryTask = retryExecutor.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun resetRetry() {
        synchronized(this) {
            retryTask?.cancel(false)
            retryTask = null
            retryAttempt = 0
        }
    }

    // ---- protocol handling ----

    private fun handleItem(item: Any) {
        when (item) {
            is Long -> when (item.toInt()) {
                0xA1 -> {
                    Log.i(TAG, "ACK: stream started")
                    _status.value = UsbLinkState.STREAMING
                    // The device sends the registry only on its first 'S' per
                    // USB session (DTR toggle). If this app instance never
                    // sees an entry (e.g. the app restarted while the device
                    // stayed powered), ask for a re-send once the paced send
                    // would have finished.
                    retryExecutor.schedule(
                        { requestRegistryIfMissing() },
                        REGISTRY_GRACE_MS,
                        TimeUnit.MILLISECONDS,
                    )
                }
                0xA0 -> {
                    Log.i(TAG, "ACK: stream paused")
                    _status.value = UsbLinkState.CONNECTED
                }
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val m = item as Map<Any, Any>
                when ((m[1] as? Number)?.toLong()) {
                    0L -> registerSignal(m)   // registry: {1:0, ...}
                    1L -> updateSignals(m)    // data: {1:1, 2:[[index, raw], ...]}
                }
            }
        }
    }

    private fun registerSignal(m: Map<Any, Any>) {
        val index = (m[3] as? Number)?.toLong() ?: return
        val name = m[4] as? String ?: return
        (m[2] as? Number)?.toInt()?.let { registryTotal = it }
        signalMeta[index] = SignalMeta(
            name = name,
            unit = m[5] as? String ?: "",
            scale = (m[6] as? Number)?.toFloat() ?: 1f,
            offset = (m[7] as? Number)?.toFloat() ?: 0f,
        )
        registryCount++
        if (!registrySeen) {
            registrySeen = true
            _status.value = UsbLinkState.STREAMING
        }
        if (registryCount % 32 == 0) {
            Log.i(TAG, "Registry complete: $registryCount signals")
        }
    }

    /** Sends 'R' (registry re-send) if this session has no usable registry:
     *  none seen at all (device skipped it after an app restart without a DTR
     *  drop) or entries lost to TX contention. */
    private fun requestRegistryIfMissing() {
        if (!connected || registryRequested) return
        if (!registrySeen || (registryTotal > 0 && registryCount < registryTotal)) {
            registryRequested = true
            Log.w(TAG, "Registry missing/incomplete ($registryCount/$registryTotal) — requesting re-send")
            registryCount = 0
            executor.execute { writeCommand(CMD_REGISTRY) }
        }
    }

    private fun updateSignals(m: Map<Any, Any>) {
        val pairs = m[2] as? List<*> ?: return
        if (!dataSeen) {
            dataSeen = true
            _status.value = UsbLinkState.DATA
        }
        synchronized(signalValues) {
            for (p in pairs) {
                val pair = p as? List<*> ?: continue
                val index = (pair.getOrNull(0) as? Number)?.toLong() ?: continue
                val raw = (pair.getOrNull(1) as? Number)?.toFloat() ?: continue
                val meta = signalMeta[index] ?: continue
                // The firmware sends FINAL values (scale/offset already applied
                // in extract_signal_value) — the registry scale/offset are
                // metadata only. Re-applying them here would corrupt readings.
                signalValues[meta.name] = raw
            }
        }
    }

    private fun buildSnapshot(values: Map<String, Float>): CarSnapshot {
        fun v(name: String, default: Float = 0f) = values[name] ?: default
        fun lit(name: String) = v(name) >= 0.5f
        // Derived power: P ≈ torque × rpm, torque ≈ throttle (first-order).
        // Accepts both normalized (0..1) and real units (% / RPM) from the firmware.
        val throttle = v("THROTTLE")
        val rpm = v("ENGINE_RPM")
        val throttleFrac = (if (throttle > 1f) throttle / 100f else throttle).coerceIn(0f, 1f)
        val rpmFrac = (if (rpm > 1f) rpm / 8000f else rpm).coerceIn(0f, 1f)
        val power = if (v("BRAKE_PRESSURE") > 0.5f) 0f else (throttleFrac * rpmFrac).coerceIn(0f, 1f)
        return CarSnapshot(
            speed = SpeedInfo(kmh = v("SPEED").roundToInt()),
            power = power,
            climate = ClimateInfo(tempC = v("COOLANT_TEMP").roundToInt(), fanLevel = 4),
            steeringFraction = ((v("LW1_STEERING_ANGLE") / 45f).coerceIn(-1f, 1f) + 1f) / 2f,
            highBeam = lit("HIGH_BEAM"),
            turnLeftLamp = lit("TURN_LEFT_LAMP"),
            turnRightLamp = lit("TURN_RIGHT_LAMP"),
            fogLight = lit("FOG_LIGHT"),
            chargeWarning = lit("CHARGE_WARNING"),
            fuelLevel = v("FUEL_LEVEL").coerceIn(0f, 1f),
            batteryVoltage = v("BATTERY_VOLTAGE"),
        )
    }

    private data class SignalMeta(
        val name: String,
        val unit: String,
        val scale: Float,
        val offset: Float,
    )

    private companion object {
        const val TAG = "UsbEsp32DataSource"
        const val ACTION_USB_PERMISSION = "com.example.carheadunit.USB_PERMISSION"
        const val VID = 0x303A
        // Firmware is pinned to PID 0x4000 (sdkconfig CONFIG_TINYUSB_DESC_CUSTOM_PID).
        // 0x4001 is the esp_tinyusb auto-PID (0x4000 | CFG_TUD_CDC) that devices
        // report until the firmware with the pin is flashed — accept both.
        const val PID = 0x4000
        const val PID_ESP_TINYUSB_AUTO = 0x4001
        const val CMD_START = 0x53    // 'S'
        const val CMD_PAUSE = 0x50    // 'P'
        const val CMD_REGISTRY = 0x52 // 'R' — re-send the signal registry
        const val MAX_RETRY_ATTEMPTS = 5
        // Consecutive failed bulk reads before declaring the link dead.
        // 4 × 200 ms poll = 800 ms of tolerated silence, comfortably above
        // the firmware's 500 ms heartbeat on a quiet CAN bus.
        const val READ_FAILURE_LIMIT = 4
        const val RETRY_BASE_DELAY_MS = 10_000L
        const val RETRY_MAX_DELAY_MS = 60_000L
        // Registry is paced at 20 ms per entry (32 entries ≈ 640 ms); check
        // for lost entries a safe margin after the first entry arrives.
        const val REGISTRY_GRACE_MS = 1_500L
    }
}
