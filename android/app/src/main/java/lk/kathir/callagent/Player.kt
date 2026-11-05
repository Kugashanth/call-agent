package lk.kathir.callagent

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Plays a WAV and blocks until it finishes (call from a background thread). */
class Player(private val ctx: Context) {
    fun play(wav: ByteArray, usage: Int) {
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
