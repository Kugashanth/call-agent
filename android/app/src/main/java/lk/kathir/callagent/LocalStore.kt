package lk.kathir.callagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Standalone mode storage: call records + knowledge text, kept only in the app's private storage on this phone. */
object LocalStore {
    private val SAFE = Regex("^[A-Za-z0-9_-]{1,64}$")

    private fun dir(c: Context) = File(c.filesDir, "calls").apply { mkdirs() }
    private fun knowledgeFile(c: Context) = File(c.filesDir, "knowledge.md")
    private fun file(c: Context, sid: String): File {
        if (!SAFE.matches(sid)) throw IOException("Invalid id")
        return File(dir(c), "$sid.json")
    }

    fun knowledge(c: Context): String {
        val f = knowledgeFile(c)
        return if (f.exists()) f.readText() else defaultKnowledge(Prefs.ownerName(c))
    }

    fun setKnowledge(c: Context, text: String) = knowledgeFile(c).writeText(text.take(20_000))

    fun saveCall(c: Context, call: JSONObject) = file(c, call.getString("sid")).writeText(call.toString())

    fun listCalls(c: Context, limit: Int = 50): JSONArray {
        val out = JSONArray()
        dir(c).listFiles { f -> f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.forEach { f ->
                try {
                    val j = JSONObject(f.readText())
                    j.put("turns", j.optJSONArray("turns")?.length() ?: 0)
                    out.put(j)
                } catch (e: Exception) { /* skip corrupt file */ }
            }
        return out
    }

    fun getCall(c: Context, sid: String): JSONObject {
        val f = file(c, sid)
        if (!f.exists()) throw IOException("Not found")
        return JSONObject(f.readText())
    }

    fun deleteCall(c: Context, sid: String) { file(c, sid).delete() }

    fun defaultKnowledge(owner: String) = """
        ## About
        - Owner: $owner

        ## Availability
        - Usually busy on weekdays 8 AM – 4 PM.
        - Best time to reach $owner: weekdays after 6 PM, or weekends.
        - For urgent matters, send a WhatsApp message to this number.

        ## Common questions
        - Projects / work: take the caller's name, topic and deadline.

        ## Do NOT share
        - Home address, family details, other phone numbers, or anything not written above.
    """.trimIndent()
}
