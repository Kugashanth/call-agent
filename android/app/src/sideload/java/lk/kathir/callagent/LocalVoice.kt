package lk.kathir.callagent

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Standalone voice: Android's built-in Text-to-Speech (Google TTS has Tamil and Sinhala voices).
 * Create on the main thread; call synthesize() only from a background thread.
 */
class LocalVoice(ctx: Context) : TextToSpeech.OnInitListener {
    private val ready = CountDownLatch(1)
    @Volatile private var ok = false
    private val tts = TextToSpeech(ctx.applicationContext, this)

    override fun onInit(status: Int) {
        ok = status == TextToSpeech.SUCCESS
        if (!ok) AppLog.e("Text-to-speech engine failed to start")
        ready.countDown()
    }

    @Suppress("DEPRECATION")
    private fun locale(lang: String): Locale = when (lang) {
        "ta" -> Locale("ta", "IN")
        "si" -> Locale("si", "LK")
        else -> Locale("en", "IN")
    }

    private fun waitReady(): Boolean {
        ready.await(10, TimeUnit.SECONDS)
        return ok
    }

    /** True if a voice for this language is installed (background thread only). */
    fun hasVoice(lang: String): Boolean =
        waitReady() && tts.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE

    /** Writes speech to [out] as WAV. Background thread only. */
    @Synchronized
    fun synthesize(text: String, lang: String, out: File): Boolean {
        if (!waitReady()) return false
        val loc = locale(lang)
        if (tts.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = loc
        } else {
            AppLog.e("No ${lang.uppercase()} voice installed — using English voice (install it in Text-to-speech settings)")
            tts.language = Locale.ENGLISH
        }
        val id = UUID.randomUUID().toString()
        val done = CountDownLatch(1)
        var success = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) { success = true; done.countDown() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { done.countDown() }
            override fun onError(utteranceId: String?, errorCode: Int) { done.countDown() }
        })
        if (tts.synthesizeToFile(text, Bundle(), out, id) != TextToSpeech.SUCCESS) return false
        done.await(30, TimeUnit.SECONDS)
        return success && out.length() > 44
    }

    fun shutdown() {
        try { tts.shutdown() } catch (e: Exception) {}
    }
}
