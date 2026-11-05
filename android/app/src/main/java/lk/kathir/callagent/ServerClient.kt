package lk.kathir.callagent

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Reply(
    val reply: String,
    val lang: String,
    val endCall: Boolean,
    val heard: String,
    val audio: ByteArray?,
)

/** Talks to the Python server's /phone API. Blocking calls — use from a background thread. */
class ServerClient(private val baseUrl: String, private val key: String) {

    fun start(session: String, from: String?): Reply =
        parse(post("/phone/start?session=${enc(session)}&from=${enc(from ?: "")}", ByteArray(0)))

    /** wav = 16 kHz mono 16-bit WAV, or empty array for "caller said nothing". */
    fun turn(session: String, wav: ByteArray): Reply =
        parse(post("/phone/turn?session=${enc(session)}", wav))

    fun end(session: String) {
        post("/phone/end?session=${enc(session)}", ByteArray(0))
    }

    fun health(): String {
        val conn = URL("$baseUrl/health").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            "HTTP ${conn.responseCode}: " + conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun post(path: String, body: ByteArray): JSONObject {
        if (baseUrl.isEmpty()) throw IOException("Server URL is not set")
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.setRequestProperty("X-Agent-Key", key)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(200)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(j: JSONObject): Reply {
        val b64 = j.optString("audio_b64", "")
        return Reply(
            reply = j.optString("reply", ""),
            lang = j.optString("lang", "en"),
            endCall = j.optBoolean("end_call", false),
            heard = j.optString("heard", ""),
            audio = if (b64.isEmpty()) null else Base64.decode(b64, Base64.DEFAULT),
        )
    }
}
