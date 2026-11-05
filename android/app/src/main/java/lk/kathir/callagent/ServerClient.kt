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
        checkUrl()
        val conn = URL("$baseUrl/health").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            "HTTP ${conn.responseCode}: " + conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Only allow https://, or plain http:// to a private LAN address (laptop on the same Wi-Fi). */
    private fun checkUrl() {
        if (baseUrl.isEmpty()) throw IOException("Server URL is not set")
        if (!isAllowedUrl(baseUrl)) throw IOException("Use https:// (http:// is only allowed for LAN addresses like 192.168.x.x)")
    }

    companion object {
        fun isAllowedUrl(url: String): Boolean {
            val u = try { URL(url) } catch (e: Exception) { return false }
            if (u.protocol == "https") return true
            if (u.protocol != "http") return false
            val h = u.host
            if (h == "localhost" || h == "127.0.0.1") return true
            if (h.startsWith("10.") || h.startsWith("192.168.")) return true
            if (h.startsWith("172.")) {
                val second = h.split(".").getOrNull(1)?.toIntOrNull() ?: return false
                return second in 16..31
            }
            return false
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun post(path: String, body: ByteArray): JSONObject {
        checkUrl()
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
