package lk.kathir.callagent

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal WAV helpers: parse PCM16 WAVs of any rate/channels, resample, and join into one 16 kHz mono file. */
object WavUtil {
    const val RATE = 16000

    /** Returns mono 16-bit samples at [RATE] Hz, or null if the bytes are not a PCM16 WAV. */
    fun toMono16k(wav: ByteArray): ShortArray? {
        if (wav.size < 44) return null
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        if (String(wav, 0, 4) != "RIFF" || String(wav, 8, 4) != "WAVE") return null
        var pos = 12
        var channels = 1
        var rate = RATE
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4)
            val len = bb.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                channels = bb.getShort(body + 2).toInt()
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt()
            } else if (id == "data") {
                dataStart = body
                // some encoders write 0 / -1 for streaming; clamp to file size
                dataLen = if (len <= 0 || body + len > wav.size) wav.size - body else len
                break
            }
            if (len < 0) return null
            pos = body + len + (len and 1)
        }
        if (dataStart < 0 || bits != 16 || channels < 1) return null
        val frames = dataLen / (2 * channels)
        val mono = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (ch in 0 until channels) sum += bb.getShort(dataStart + (i * channels + ch) * 2)
            mono[i] = (sum / channels).toShort()
        }
        return resample(mono, rate, RATE)
    }

    private fun resample(a: ShortArray, src: Int, dst: Int): ShortArray {
        if (src == dst || a.isEmpty()) return a
        val n = (a.size.toLong() * dst / src).toInt()
        val out = ShortArray(n)
        val step = src.toDouble() / dst
        for (i in 0 until n) {
            val x = i * step
            val j = x.toInt()
            val f = x - j
            val cur = a[j.coerceAtMost(a.size - 1)].toInt()
            val nxt = a[(j + 1).coerceAtMost(a.size - 1)].toInt()
            out[i] = (cur * (1 - f) + nxt * f).toInt().toShort()
        }
        return out
    }

    /** Joins WAV pieces (any format) into one 16 kHz mono WAV file with short gaps between pieces. */
    fun join(pieces: List<ByteArray>, out: File): Boolean {
        val parts = pieces.mapNotNull { toMono16k(it) }
        if (parts.isEmpty()) return false
        val gap = RATE / 4
        val total = parts.sumOf { it.size + gap }
        val pcm = ByteBuffer.allocate(total * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (p in parts) {
            for (s in p) pcm.putShort(s)
            repeat(gap) { pcm.putShort(0) }
        }
        out.parentFile?.mkdirs()
        out.writeBytes(header(total * 2) + pcm.array())
        return true
    }

    fun durationSec(file: File): Int = ((file.length() - 44).coerceAtLeast(0) / (RATE * 2)).toInt()

    private fun header(dataBytes: Int): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + dataBytes); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataBytes)
        return h.array()
    }
}
