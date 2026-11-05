package lk.kathir.callagent

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records ONE caller utterance from the microphone using a simple energy-based voice-activity detector:
 * calibrate noise -> wait for speech -> stop after [silenceMs] of silence. Returns a 16 kHz WAV, or null if
 * nobody spoke within [noSpeechTimeoutMs].
 */
class UtteranceRecorder(private val audioSource: Int) {
    companion object {
        const val RATE = 16000
        private const val FRAME_MS = 30
        private const val FRAME = RATE * FRAME_MS / 1000      // 480 samples
        private const val MIN_RMS = 250.0                      // absolute floor for "speech"
        private const val CALIBRATION_FRAMES = 8               // ~240 ms
        private const val START_FRAMES = 3                     // ~90 ms of loud audio = speech started
        private const val PREROLL_FRAMES = 10                  // keep ~300 ms before speech start
    }

    @SuppressLint("MissingPermission")
    fun record(
        shouldStop: () -> Boolean,
        silenceMs: Int = 1000,
        noSpeechTimeoutMs: Int = 7000,
        maxSpeechMs: Int = 15000,
    ): ByteArray? {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            audioSource, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, FRAME * 2 * 20),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("Microphone could not be opened (permission or accessibility service off?)")
        }

        val buf = ShortArray(FRAME)
        val pcm = ByteArrayOutputStream()
        val preroll = ArrayDeque<ShortArray>()
        var noise = 0.0
        var calibrated = 0
        var loudRun = 0
        var inSpeech = false
        var silence = 0
        var speech = 0
        var waited = 0
        var peak = 0.0

        rec.startRecording()
        try {
            while (!shouldStop()) {
                val n = rec.read(buf, 0, FRAME)
                if (n <= 0) continue
                val chunk = buf.copyOf(n)
                val level = rms(chunk)
                peak = max(peak, level)

                if (calibrated < CALIBRATION_FRAMES) {          // learn background noise level
                    noise += level / CALIBRATION_FRAMES
                    calibrated++
                    preroll.addLast(chunk)
                    continue
                }
                val loud = level > max(noise * 2.5, MIN_RMS)

                if (!inSpeech) {
                    waited += FRAME_MS
                    preroll.addLast(chunk)
                    if (preroll.size > PREROLL_FRAMES) preroll.removeFirst()
                    loudRun = if (loud) loudRun + 1 else 0
                    if (!loud) noise = noise * 0.95 + level * 0.05
                    if (loudRun >= START_FRAMES) {
                        inSpeech = true
                        preroll.forEach { writePcm(pcm, it) }
                        preroll.clear()
                    } else if (waited >= noSpeechTimeoutMs) {
                        break
                    }
                } else {
                    writePcm(pcm, chunk)
                    speech += FRAME_MS
                    silence = if (loud) 0 else silence + FRAME_MS
                    if (silence >= silenceMs || speech >= maxSpeechMs) break
                }
            }
        } finally {
            try { rec.stop() } catch (e: Exception) {}
            rec.release()
        }

        AppLog.i("Mic: peak=${peak.toInt()} noise=${noise.toInt()} speech=${speech}ms" +
            if (peak < 1.0) "  <-- SILENCE: mic is blocked during the call" else "")
        return if (inSpeech) wav(pcm.toByteArray()) else null
    }

    private fun rms(s: ShortArray): Double {
        var sum = 0.0
        for (v in s) sum += v.toDouble() * v
        return sqrt(sum / s.size)
    }

    private fun writePcm(out: ByteArrayOutputStream, s: ShortArray) {
        val bb = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in s) bb.putShort(v)
        out.write(bb.array())
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + pcm.size); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(pcm.size)
        return h.array() + pcm
    }
}
