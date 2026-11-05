package lk.kathir.callagent

import android.util.Base64
import org.json.JSONArray
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

/**
 * Talks to your own Call Agent server. Blocking calls — use from a background thread.
 * Server URL "demo" = built-in sample data (no server needed; used for Google Play review).
 */
class ServerClient(private val baseUrl: String, private val key: String, private val ctx: android.content.Context? = null) {

    companion object {
        const val DEMO = "demo"
        const val LOCAL = "local"   // standalone mode: records stored on this phone (needs ctx)

        /** Only allow https://, or plain http:// to a private LAN address (laptop on the same Wi-Fi). */
        fun isAllowedUrl(url: String): Boolean {
            if (url == DEMO || url == LOCAL) return true
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

    val isDemo get() = baseUrl == DEMO
    val isLocal get() = baseUrl == LOCAL && ctx != null

    // ---------- phone API (sideload auto-answer app) ----------
    fun start(session: String, from: String?): Reply =
        parseReply(JSONObject(request("POST", "/phone/start?session=${enc(session)}&from=${enc(from ?: "")}", ByteArray(0), "audio/wav")))

    /** wav = 16 kHz mono 16-bit WAV, or empty array for "caller said nothing". */
    fun turn(session: String, wav: ByteArray): Reply =
        parseReply(JSONObject(request("POST", "/phone/turn?session=${enc(session)}", wav, "audio/wav")))

    fun end(session: String) {
        request("POST", "/phone/end?session=${enc(session)}", ByteArray(0), "audio/wav")
    }

    // ---------- dashboard API (both apps) ----------
    fun health(): String = if (isLocal) "Standalone (this phone)" else if (isDemo) "Demo mode (no server)" else "OK: " + request("GET", "/health")

    fun status(): JSONObject = if (isLocal) JSONObject().put("owner", Prefs.ownerName(ctx!!)).put("twilio_number", "")
        else if (isDemo) Demo.status() else JSONObject(request("GET", "/api/status"))

    fun listCalls(limit: Int = 50): JSONArray = if (isLocal) LocalStore.listCalls(ctx!!, limit) else if (isDemo) Demo.calls() else JSONArray(request("GET", "/api/calls?limit=$limit"))

    fun getCall(sid: String): JSONObject = if (isLocal) LocalStore.getCall(ctx!!, sid) else if (isDemo) Demo.call(sid) else JSONObject(request("GET", "/api/calls/${enc(sid)}"))

    /** Full-call voice recording (WAV bytes). */
    fun getAudio(sid: String): ByteArray = when {
        isLocal -> throw IOException("Recordings are only available with a server")
        isDemo -> Demo.audio()
        else -> requestBytes("/api/calls/${enc(sid)}/audio")
    }

    fun deleteCall(sid: String) {
        if (isLocal) LocalStore.deleteCall(ctx!!, sid) else if (isDemo) Demo.delete(sid) else request("DELETE", "/api/calls/${enc(sid)}")
    }

    fun getKnowledge(): String = if (isLocal) LocalStore.knowledge(ctx!!) else if (isDemo) Demo.knowledge else JSONObject(request("GET", "/api/knowledge")).getString("text")

    fun putKnowledge(text: String) {
        if (isLocal) { LocalStore.setKnowledge(ctx!!, text); return }
        if (isDemo) { Demo.knowledge = text; return }
        request("PUT", "/api/knowledge", JSONObject().put("text", text).toString().toByteArray(), "application/json")
    }

    // ---------- HTTP ----------
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(method: String, path: String, body: ByteArray? = null, contentType: String = "application/json"): String {
        if (baseUrl.isEmpty()) throw IOException("Server URL is not set")
        if (!isAllowedUrl(baseUrl)) throw IOException("Use https:// (http:// is only allowed for LAN addresses like 192.168.x.x)")
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("X-Agent-Key", key)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401) throw IOException("Wrong agent key (HTTP 401)")
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun requestBytes(path: String): ByteArray {
        if (!isAllowedUrl(baseUrl)) throw IOException("Use https://")
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("X-Agent-Key", key)
            val code = conn.responseCode
            if (code == 404) throw IOException("No recording for this call")
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseReply(j: JSONObject): Reply {
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

/** Sample data for demo mode (server URL = "demo"). */
private object Demo {
    var knowledge = "## About\n- Owner: Demo User\n\n## Availability\n- Weekdays after 6 PM.\n"
    private val deleted = mutableSetOf<String>()
    private val now = System.currentTimeMillis() / 1000.0

    private val records = listOf(
        JSONObject().put("sid", "demo1").put("from", "+94771234567").put("started", now - 3600).put("ended", now - 3500)
            .put("channel", "twilio").put("lang", "ta").put("caller_name", "Ravi").put("has_recording", true)
            .put("collected", JSONObject().put("name", "Ravi").put("reason", "Data science project")
                .put("callback", "0771234567, after 6 PM").put("urgent", "no"))
            .put("messages", JSONArray().put("Ravi wants to discuss a data science project; call back after 6 PM."))
            .put("turns", JSONArray()
                .put(JSONObject().put("caller", "Hello, Kathir irukkara? Naan Ravi.").put("agent", "வணக்கம் Ravi! Kathir இப்போ busy. ஏதாவது message சொல்லணுமா?"))
                .put(JSONObject().put("caller", "Project pathi pesanum, evening call pannunga.").put("agent", "சரி, Kathir-க்கு சொல்றேன். நன்றி!"))),
        JSONObject().put("sid", "demo2").put("from", "+94712223344").put("started", now - 86400).put("ended", now - 86300)
            .put("channel", "twilio").put("lang", "si").put("caller_name", "Nimal").put("has_recording", true)
            .put("collected", JSONObject().put("name", "Nimal").put("reason", "Group assignment deadline").put("urgent", "yes"))
            .put("messages", JSONArray().put("Nimal asked about the group assignment deadline."))
            .put("turns", JSONArray()
                .put(JSONObject().put("caller", "Ayubowan, assignment eka gana kathakaranna one.").put("agent", "ආයුබෝවන් Nimal! මම Kathir ට message එක දෙන්නම්."))),
        JSONObject().put("sid", "demo3").put("from", "+447700900123").put("started", now - 172800).put("ended", now - 172750)
            .put("channel", "twilio").put("lang", "en").put("caller_name", JSONObject.NULL).put("has_recording", false)
            .put("collected", JSONObject())
            .put("messages", JSONArray())
            .put("turns", JSONArray()
                .put(JSONObject().put("caller", "Is he available tomorrow morning?").put("agent", "He's usually in lectures on weekday mornings; the best time is after 6 PM."))),
    )

    fun status(): JSONObject = JSONObject().put("owner", "Demo User").put("twilio_number", "+14155550123")

    fun calls(): JSONArray {
        val a = JSONArray()
        for (r in records) if (r.getString("sid") !in deleted) {
            a.put(JSONObject(r.toString()).put("turns", r.getJSONArray("turns").length()))
        }
        return a
    }

    fun call(sid: String): JSONObject =
        records.firstOrNull { it.getString("sid") == sid && sid !in deleted } ?: throw IOException("Not found")

    fun delete(sid: String) { deleted.add(sid) }

    /** 2-second demo "recording": two soft tones (8 kHz 16-bit mono WAV). */
    fun audio(): ByteArray {
        val rate = 8000
        val n = rate * 2
        val pcm = java.nio.ByteBuffer.allocate(n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val f = if (i < n / 2) 440.0 else 660.0
            pcm.putShort((3000 * Math.sin(2 * Math.PI * f * i / rate)).toInt().toShort())
        }
        val data = pcm.array()
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + data.size); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(rate); h.putInt(rate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(data.size)
        return h.array() + data
    }
}
