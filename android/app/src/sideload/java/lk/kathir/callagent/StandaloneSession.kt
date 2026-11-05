package lk.kathir.callagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Fixed phrases in the three languages (the AI writes everything else). */
object Phrases {
    fun greeting(lang: String, owner: String) = when (lang) {
        "ta" -> "வணக்கம்! இது $owner அவர்களின் AI உதவியாளர்."
        "si" -> "ආයුබෝවන්! මේ ${owner}ගේ AI සහායකයා."
        else -> "Hello! You've reached $owner's AI assistant. You can speak in Tamil, Sinhala or English. How can I help you?"
    }

    fun reprompt(lang: String) = when (lang) {
        "ta" -> "மன்னிக்கவும், சரியாக கேட்கவில்லை. மீண்டும் சொல்லுங்கள்."
        "si" -> "සමාවන්න, හරියට ඇහුණේ නැහැ. කරුණාකර නැවත කියන්න."
        else -> "Sorry, I didn't catch that. Could you say it again?"
    }

    fun goodbye(lang: String) = when (lang) {
        "ta" -> "நன்றி! வணக்கம்."
        "si" -> "ස්තූතියි! ආයුබෝවන්."
        else -> "Thank you for calling. Goodbye!"
    }

    fun error(lang: String, owner: String) = when (lang) {
        "ta" -> "மன்னிக்கவும், ஒரு தொழில்நுட்ப பிரச்சனை. $owner உங்களை பிறகு அழைப்பார்."
        "si" -> "සමාවන්න, තාක්ෂණික ගැටලුවක්. $owner පසුව ඔබට කතා කරයි."
        else -> "Sorry, there's a technical problem. $owner will call you back."
    }
}

/**
 * One full conversation, entirely on the phone: greet -> loop { record -> Gemini -> TTS -> play }.
 * Used for real calls (CallAgentService) and for the "Test conversation" button.
 * Returns true if the conversation ended normally (caller can be hung up on).
 */
class StandaloneSession(
    private val ctx: Context,
    private val voice: LocalVoice,
    private val usage: Int,
    private val from: String?,
    private val channel: String,
    private val record: () -> ByteArray?,
    private val shouldStop: () -> Boolean,
) {
    companion object {
        const val MAX_TURNS = 12
        const val MAX_NO_INPUT = 2
    }

    private val owner = Prefs.ownerName(ctx)
    private val player = Player(ctx)
    private val call = JSONObject()
        .put("sid", "L" + UUID.randomUUID().toString().replace("-", "").take(20))
        .put("from", from ?: "Unknown")
        .put("started", System.currentTimeMillis() / 1000.0)
        .put("channel", channel)
        .put("lang", "en")
        .put("caller_name", JSONObject.NULL)
        .put("messages", JSONArray())
        .put("turns", JSONArray())

    fun run(): Boolean {
        val brain = LocalBrain(ctx)
        var lang = "en"
        var noInput = 0
        var ended = false
        try {
            for (l in listOf("ta", "si", "en")) {
                if (shouldStop()) return false
                speak(Phrases.greeting(l, owner), l)
            }
            var turns = 0
            while (!shouldStop() && turns < MAX_TURNS) {
                val wav = record()
                if (shouldStop()) break

                val t = if (wav == null) null else brain.respond(wav)
                if (t == null || t.heard.isBlank()) {
                    noInput++
                    if (noInput > MAX_NO_INPUT) { speak(Phrases.goodbye(lang), lang); ended = true; break }
                    speak(if (t != null && t.reply.isNotBlank()) t.reply else Phrases.reprompt(lang), t?.lang ?: lang)
                    continue
                }
                noInput = 0
                lang = t.lang
                AppLog.i("Caller [${t.lang}]: ${t.heard}\nAgent: ${t.reply}")
                call.put("lang", lang)
                t.callerName?.let { call.put("caller_name", it) }
                t.message?.let { call.getJSONArray("messages").put(it) }
                call.getJSONArray("turns").put(JSONObject().put("caller", t.heard).put("agent", t.reply).put("lang", t.lang))
                save()

                if (shouldStop()) break
                speak(t.reply.ifBlank { Phrases.reprompt(lang) }, lang)
                turns++
                if (t.endCall) { ended = true; break }
                if (turns >= MAX_TURNS) { speak(Phrases.goodbye(lang), lang); ended = true }
            }
        } catch (e: InterruptedException) {
            return false
        } catch (e: Exception) {
            AppLog.e("Standalone error: ${e.message}")
            if (!shouldStop()) {
                try { speak(Phrases.error(lang, owner), lang) } catch (x: Exception) {}
            }
            ended = true
        } finally {
            call.put("ended", System.currentTimeMillis() / 1000.0)
            save()
        }
        return ended
    }

    private fun save() {
        if (call.getJSONArray("turns").length() == 0) return   // don't log empty calls
        try { LocalStore.saveCall(ctx, call) } catch (e: Exception) { AppLog.e("Save failed: $e") }
    }

    private fun speak(text: String, lang: String) {
        val f = File.createTempFile("say", ".wav", ctx.cacheDir)
        try {
            if (voice.synthesize(text, lang, f)) player.play(f.readBytes(), usage)
            else AppLog.e("Could not synthesize speech ($lang)")
        } finally {
            f.delete()
        }
    }
}
