package lk.kathir.callagent

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Plays a WAV and blocks until it finishes (call from a background thread). */
class Player(private val ctx: Context) {
    companion object {
        /** The phone's "into the call" output (TYPE_TELEPHONY), if this phone exposes it. */
        fun telephonyOut(ctx: Context): AudioDeviceInfo? =
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_TELEPHONY }

        /** Logs every audio input/output the phone offers right now (useful during a call). */
        fun logDevices(ctx: Context) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            fun names(flag: Int) = am.getDevices(flag).joinToString { "${it.type}:${it.productName}" }
            AppLog.i("Audio outputs: ${names(AudioManager.GET_DEVICES_OUTPUTS)}")
            AppLog.i("Audio inputs: ${names(AudioManager.GET_DEVICES_INPUTS)}")
            AppLog.i(if (telephonyOut(ctx) != null) "Direct-to-call output AVAILABLE" else "No direct-to-call output on this phone (using speaker)")
        }
    }

    private var warned = false

    /** direct = try to send audio straight into the call line; falls back to the speaker (alarm stream). */
    fun play(wav: ByteArray, usage: Int, direct: Boolean = false) {
        if (direct) {
            val dev = telephonyOut(ctx)
            if (dev != null) {
                try { playOn(wav, AudioAttributes.USAGE_VOICE_COMMUNICATION, dev); return }
                catch (e: Exception) { if (!warned) { AppLog.e("Direct-to-call failed: $e, using speaker"); warned = true } }
            }
            playOn(wav, AudioAttributes.USAGE_ALARM, null)
            return
        }
        playOn(wav, usage, null)
    }

    private fun playOn(wav: ByteArray, usage: Int, device: AudioDeviceInfo?) {
        val f = File.createTempFile("reply", ".wav", ctx.cacheDir)
        f.writeBytes(wav)
        val done = CountDownLatch(1)
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            if (device != null) mp.setPreferredDevice(device)
            mp.setOnCompletionListener { done.countDown() }
            mp.setOnErrorListener { _, what, extra ->
                AppLog.e("Playback error $what/$extra"); done.countDown(); true
            }
            mp.prepare()
            mp.start()
            done.await(90, TimeUnit.SECONDS)
        } finally {
            try { mp.release() } catch (e: Exception) {}
            f.delete()
        }
    }
}
