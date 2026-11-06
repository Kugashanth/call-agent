package lk.kathir.callagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Fixed phrases in the three languages (the AI writes everything else). */
object Phrases {
    /** Opening language menu: each line is spoken in its own language's voice. */
    fun languageMenu(lang: String) = when (lang) {
        "ta" -> "வணக்கம்! தமிழில் பேச, தமிழ் என்று சொல்லுங்கள்."
        "si" -> "ආයුබෝවන්! සිංහලෙන් කතා කිරීමට, සිංහල කියන්න."
        else -> "Hello! For English, please say English."
    }

    fun menuRepeat(lang: String) = when (lang) {
        "ta" -> "மன்னிக்கவும், கேட்கவில்லை. தமிழ், சிங்களம், அல்லது English என்று சொல்லுங்கள்."
        "si" -> "සමාවන්න, ඇහුණේ නැහැ. සිංහල, දෙමළ, හෝ English කියන්න."
        else -> "Sorry, I didn't catch that. Please say Tamil, Sinhala or English."
    }

    fun recordingNotice(lang: String) = when (lang) {
        "ta" -> "இந்த அழைப்பு பதிவு செய்யப்படுகிறது."
        "si" -> "මෙම ඇමතුම පටිගත කෙරේ."
        else -> "This call is recorded."
    }

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
    private val direct: Boolean = false,
    private val record: () -> ByteArray?,
    private val shouldStop: () -> Boolean,
) {
    companion object {
        const val MAX_TURNS = 12
        const val MAX_NO_INPUT = 2
    }

    private val owner = Prefs.ownerName(ctx)
    private val player = Player(ctx)
    private val recordOn = Prefs.recordCalls(ctx) && channel != "test"
    private val pieces = mutableListOf<ByteArray>()          // caller + agent audio, in order
    private val collected = JSONObject()
    private val call = JSONObject()
        .put("sid", "L" + UUID.randomUUID().toString().replace("-", "").take(20))
        .put("from", from ?: "Unknown")
        .put("started", System.currentTimeMillis() / 1000.0)
        .put("channel", channel)
        .put("lang", "en")
        .put("caller_name", JSONObject.NULL)
        .put("messages", JSONArray())
        .put("turns", JSONArray())
        .put("collected", JSONObject())
        .put("has_recording", false)

    fun run(): Boolean {
        val brain = LocalBrain(ctx)
        var lang = "en"
        var noInput = 0
        var ended = false
        try {
            // 1) language menu (Tamil, Sinhala, English), each in its own voice
            for (l in listOf("ta", "si", "en")) {
                if (shouldStop()) return false
                speak(Phrases.languageMenu(l), l)
            }
            var langChosen = false
            var turns = 0
            while (!shouldStop() && turns < MAX_TURNS) {
                val wav = record()
                if (wav != null && recordOn) pieces.add(wav)
                if (shouldStop()) break

                val t = if (wav == null) null else brain.respond(wav)
                if (t == null || t.heard.isBlank()) {
                    noInput++
                    if (noInput > MAX_NO_INPUT) { speak(Phrases.goodbye(lang), lang); ended = true; break }
                    if (!langChosen) {
                        for (l in listOf("ta", "si", "en")) { if (!shouldStop()) speak(Phrases.menuRepeat(l), l) }
                    } else {
                        speak(if (t != null && t.reply.isNotBlank()) t.reply else Phrases.reprompt(lang), t?.lang ?: lang)
                    }
                    continue
                }
                noInput = 0
                langChosen = true
                lang = t.lang
                AppLog.i("Caller [${t.lang}]: ${t.heard}\nAgent: ${t.reply}")
                call.put("lang", lang)
                t.callerName?.let { call.put("caller_name", it) }
                t.message?.let { call.getJSONArray("messages").put(it) }
                for ((k, v) in t.collected) collected.put(k, v)
                call.put("collected", collected)
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
            if (recordOn && call.getJSONArray("turns").length() > 0) {
                try {
                    val ok = WavUtil.join(pieces, LocalStore.recordingFile(ctx, call.getString("sid")))
                    call.put("has_recording", ok)
                } catch (e: Exception) { AppLog.e("Recording failed: $e") }
            }
            save()
            if (channel != "test" && call.getJSONArray("turns").length() > 0) {
                try { Notifier.callAnswered(ctx, call) } catch (e: Exception) {}
            }
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
            if (voice.synthesize(text, lang, f)) {
                val bytes = f.readBytes()
                if (recordOn) pieces.add(bytes)
                player.play(bytes, usage, direct)
            } else AppLog.e("Could not synthesize speech ($lang)")
        } finally {
            f.delete()
        }
    }
}
