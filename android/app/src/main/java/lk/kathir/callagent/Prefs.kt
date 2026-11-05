package lk.kathir.callagent

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

/** App settings (stored in SharedPreferences). */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("call_agent", Context.MODE_PRIVATE)

    /** Playback options for the AI voice. Which one the caller hears best depends on the phone model. */
    val PLAYBACK_NAMES = arrayOf("Media", "Alarm", "Voice call", "Assistant")
    private val PLAYBACK_USAGES = intArrayOf(
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_ALARM,
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_ASSISTANT,
    )
    private val PLAYBACK_STREAMS = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_MUSIC,
    )

    fun serverUrl(c: Context): String = sp(c).getString("url", "")!!.trim().trimEnd('/')
    fun agentKey(c: Context): String = sp(c).getString("key", "")!!.trim()
    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun delaySec(c: Context): Int = sp(c).getInt("delay", 10)
    fun silenceMs(c: Context): Int = sp(c).getInt("silence_ms", 1000)
    fun playbackIndex(c: Context): Int = sp(c).getInt("playback", 0).coerceIn(0, PLAYBACK_NAMES.size - 1)
    fun playbackUsage(c: Context): Int = PLAYBACK_USAGES[playbackIndex(c)]
    fun playbackStream(c: Context): Int = PLAYBACK_STREAMS[playbackIndex(c)]

    fun saveServer(c: Context, url: String, key: String) {
        sp(c).edit().putString("url", url.trim()).putString("key", key.trim()).apply()
    }

    fun consented(c: Context): Boolean = sp(c).getBoolean("consented", false)
    fun setConsented(c: Context, v: Boolean) { sp(c).edit().putBoolean("consented", v).apply() }

    fun save(c: Context, url: String, key: String, enabled: Boolean, delay: Int, silenceMs: Int, playback: Int) {
        sp(c).edit()
            .putString("url", url.trim())
            .putString("key", key.trim())
            .putBoolean("enabled", enabled)
            .putInt("delay", delay.coerceIn(0, 60))
            .putInt("silence_ms", silenceMs.coerceIn(400, 3000))
            .putInt("playback", playback)
            .apply()
    }
}
