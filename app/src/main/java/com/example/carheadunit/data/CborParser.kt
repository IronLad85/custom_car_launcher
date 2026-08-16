package com.example.carheadunit.data

/**
 * Minimal incremental CBOR decoder for the CAN Sniffer protocol.
 * Supports definite-length maps/arrays, unsigned/signed ints, float32,
 * and text strings — exactly what the device sends. Items may be split
 * across USB read chunks: [nextItem] returns null until an item completes.
 * Single reusable buffer, no per-item allocations beyond the decoded values.
 */
class CborParser(private val capacity: Int = 1024) {

    private val buf = ByteArray(capacity)
    private var size = 0

    fun feed(chunk: ByteArray, len: Int = chunk.size) {
        if (size + len > buf.size) return // protocol messages are tiny; drop rather than grow
        System.arraycopy(chunk, 0, buf, size, len)
        size += len
    }

    fun reset() {
        size = 0
    }

    /** Parses and removes one complete item; null if the buffer needs more bytes. */
    fun nextItem(): Any? {
        val decoded = tryDecode(buf, 0, size) ?: return null
        val (value, consumed) = decoded
        System.arraycopy(buf, consumed, buf, 0, size - consumed)
        size -= consumed
        return value
    }

    private fun tryDecode(b: ByteArray, i: Int, end: Int): Pair<Any, Int>? {
        if (i >= end) return null
        val ib = b[i].toInt() and 0xFF
        val major = ib shr 5
        val info = ib and 0x1F
        return when (major) {
            0 -> readUint(b, i, end, info)?.let { (v, ni) -> v to ni }
            1 -> readUint(b, i, end, info)?.let { (v, ni) -> (-1L - v) to ni }
            2, 3 -> { // byte / text strings (treated as UTF-8 text)
                val (len, ni) = readUint(b, i, end, info) ?: return null
                val l = len.toInt()
                if (ni + l > end) return null
                String(b, ni, l, Charsets.UTF_8) to (ni + l)
            }
            4 -> { // array
                val (n, ni) = readUint(b, i, end, info) ?: return null
                val list = ArrayList<Any>(n.toInt())
                var j = ni
                repeat(n.toInt()) {
                    val d = tryDecode(b, j, end) ?: return null
                    list.add(d.first)
                    j = d.second
                }
                list to j
            }
            5 -> { // map
                val (n, ni) = readUint(b, i, end, info) ?: return null
                val map = LinkedHashMap<Any, Any>(n.toInt())
                var j = ni
                repeat(n.toInt()) {
                    val k = tryDecode(b, j, end) ?: return null
                    val v = tryDecode(b, k.second, end) ?: return null
                    map[k.first] = v.first
                    j = v.second
                }
                map to j
            }
            7 -> when (info) {
                26 -> { // float32
                    if (i + 5 > end) return null
                    val bits = ((b[i + 1].toInt() and 0xFF) shl 24) or
                        ((b[i + 2].toInt() and 0xFF) shl 16) or
                        ((b[i + 3].toInt() and 0xFF) shl 8) or
                        (b[i + 4].toInt() and 0xFF)
                    Float.fromBits(bits).toDouble() to (i + 5)
                }
                else -> null // half-floats/tags not used by the protocol
            }
            else -> null // tags unsupported
        }
    }

    private fun readUint(b: ByteArray, i: Int, end: Int, info: Int): Pair<Long, Int>? = when {
        info < 24 -> info.toLong() to (i + 1)
        info == 24 -> if (i + 2 <= end) (b[i + 1].toLong() and 0xFF) to (i + 2) else null
        info == 25 -> if (i + 3 <= end) (((b[i + 1].toLong() and 0xFF) shl 8) or (b[i + 2].toLong() and 0xFF)) to (i + 3) else null
        info == 26 -> if (i + 5 <= end) {
            (((b[i + 1].toLong() and 0xFF) shl 24) or ((b[i + 2].toLong() and 0xFF) shl 16) or
                ((b[i + 3].toLong() and 0xFF) shl 8) or (b[i + 4].toLong() and 0xFF)) to (i + 5)
        } else null
        info == 27 -> if (i + 9 <= end) {
            var v = 0L
            for (k in 1..8) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
            v to (i + 9)
        } else null
        else -> null
    }
}
