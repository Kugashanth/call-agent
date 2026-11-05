package lk.kathir.callagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owner dashboard (the whole Google Play app): recent calls answered by the AI, caller messages,
 * transcripts, the "knowledge" the AI may share, and one-tap call-forwarding setup.
 * Calls themselves are answered in the cloud by your server (Twilio mode) — this app never touches calls or the mic.
 */
class DashboardActivity : Activity() {

    companion object {
        const val EXTRA_LOCAL = "local"
        const val PRIVACY_URL = "https://github.com/Kugashanth/call-agent/blob/main/PRIVACY.md"
    }

    private lateinit var url: EditText
    private lateinit var key: EditText
    private lateinit var status: TextView
    private lateinit var callsBox: LinearLayout
    private lateinit var knowledge: EditText
    private lateinit var fwdInfo: TextView
    private var twilioNumber: String = ""
    private var pad = 0

    private val local get() = intent.getBooleanExtra(EXTRA_LOCAL, false)

    private fun client() =
        if (local) ServerClient(ServerClient.LOCAL, "", this)
        else ServerClient(Prefs.serverUrl(this), Prefs.agentKey(this), this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad * 2) }

        fun heading(t: String) = TextView(this).apply {
            text = t; textSize = 19f; setTypeface(typeface, Typeface.BOLD); setPadding(0, pad * 2, 0, pad / 2)
        }.also { col.addView(it) }
        fun note(t: String) = TextView(this).apply { text = t; textSize = 13f; alpha = 0.75f }.also { col.addView(it) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }.also { col.addView(it) }

        status = TextView(this).apply { textSize = 15f }.also { col.addView(it) }

        // ---- Server ----
        heading("Your server")
        note("Enter your Call Agent server address and agent key, or type \"demo\" as the URL to try the app with sample data.")
        url = EditText(this).apply {
            hint = "https://your-server.example.com  or  demo"; setText(Prefs.serverUrl(this@DashboardActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; isSingleLine = true
        }.also { col.addView(it) }
        key = EditText(this).apply {
            hint = "Agent key"; setText(Prefs.agentKey(this@DashboardActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; isSingleLine = true
        }.also { col.addView(it) }
        button("Save & connect") { saveServer(); loadAll() }
        button("Try demo (sample data)") { url.setText(ServerClient.DEMO); saveServer(); loadAll() }

        // ---- Calls ----
        heading("Recent calls")
        button("↻ Refresh") { loadCalls() }
        callsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { col.addView(it) }

        // ---- Knowledge ----
        heading("What the AI may tell callers")
        note("The assistant answers ONLY from this text (availability, services, FAQs). Never put passwords or private details here.")
        knowledge = EditText(this).apply {
            minLines = 8; gravity = Gravity.TOP or Gravity.START; textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }.also { col.addView(it) }
        button("Save knowledge") { saveKnowledge() }

        // ---- Forwarding ----
        heading("Call forwarding")
        fwdInfo = note("Connect to your server to see your assistant's number.")
        button("Forward calls I don't answer → assistant") { dialCode("**004*$twilioNumber#", needsNumber = true) }
        button("Turn forwarding off") { dialCode("##004#", needsNumber = false) }
        note("Opens your phone dialer with a standard GSM code — press call to confirm. Your mobile operator may charge for forwarded calls (international rates if the assistant number is abroad).")

        // ---- About ----
        heading("Privacy")
        note("This app only talks to the server you enter. It does not use your microphone, contacts or call log.")
        button("Privacy policy") { open(PRIVACY_URL) }

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
        if (local) {
            url.isEnabled = false; key.isEnabled = false
            url.setText("Standalone mode — stored on this phone")
        }
        if (local || Prefs.serverUrl(this).isNotEmpty()) loadAll() else status.text = "Not connected. Enter your server below, or try the demo."
    }

    // ---------------- actions ----------------
    private fun saveServer() {
        if (local) { toast("Standalone mode: no server needed"); return }
        val u = url.text.toString().trim().trimEnd('/')
        if (u.isNotEmpty() && !ServerClient.isAllowedUrl(u)) {
            toast("Use https:// (http:// only for LAN addresses like 192.168.x.x)")
            return
        }
        Prefs.saveServer(this, u, key.text.toString())
    }

    private fun loadAll() {
        status.text = "Connecting…"
        bg({ client().status() }) { s ->
            twilioNumber = s.optString("twilio_number", "")
            val cl = client()
            status.text = (if (cl.isLocal) "📱 Standalone (this phone)" else if (cl.isDemo) "🧪 Demo mode" else "✅ Connected") + " — assistant for ${s.optString("owner")}"
            fwdInfo.text = if (cl.isLocal) "Not needed: in standalone mode this phone answers calls itself."
                else if (twilioNumber.isNotEmpty()) "Assistant number: $twilioNumber" else "Your server has no TWILIO_NUMBER set."
            loadCalls()
            loadKnowledge()
        }
    }

    private fun loadCalls() {
        bg({ client().listCalls() }) { arr ->
            callsBox.removeAllViews()
            if (arr.length() == 0) {
                callsBox.addView(TextView(this).apply { text = "No calls yet."; alpha = 0.7f })
                return@bg
            }
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val who = c.optString("caller_name").takeIf { it.isNotEmpty() && it != "null" } ?: c.optString("from", "Unknown")
                val msgs = c.optJSONArray("messages")
                val msg = if (msgs != null && msgs.length() > 0) msgs.optString(0) else "No message"
                val row = TextView(this).apply {
                    text = "📞 $who  ·  ${time(c.optDouble("started", 0.0))}  ·  ${lang(c.optString("lang"))}\n$msg"
                    textSize = 14f
                    setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                    setBackgroundColor(if (i % 2 == 0) 0x110F766E else 0x00000000)
                    setOnClickListener { showCall(c.optString("sid")) }
                }
                callsBox.addView(row)
            }
        }
    }

    private fun showCall(sid: String) {
        bg({ client().getCall(sid) }) { c -> callDialog(sid, c) }
    }

    private fun callDialog(sid: String, c: JSONObject) {
        val from = c.optString("from", "")
        val sb = StringBuilder()
        sb.append("From: ").append(from.ifEmpty { "Unknown" }).append('\n')
        sb.append("When: ").append(time(c.optDouble("started", 0.0))).append('\n')
        sb.append("Language: ").append(lang(c.optString("lang"))).append("\n\n")
        val msgs = c.optJSONArray("messages")
        if (msgs != null && msgs.length() > 0) {
            sb.append("Message:\n")
            for (i in 0 until msgs.length()) sb.append("• ").append(msgs.optString(i)).append('\n')
            sb.append('\n')
        }
        val turns = c.optJSONArray("turns")
        if (turns != null) {
            sb.append("Transcript:\n")
            for (i in 0 until turns.length()) {
                val t = turns.getJSONObject(i)
                sb.append("👤 ").append(t.optString("caller")).append('\n')
                sb.append("🤖 ").append(t.optString("agent")).append("\n\n")
            }
        }
        val d = AlertDialog.Builder(this)
            .setTitle("Call details")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .setNegativeButton("Delete") { _, _ -> confirmDelete(sid) }
        if (from.startsWith("+") || from.firstOrNull()?.isDigit() == true) {
            d.setNeutralButton("Call back") { _, _ ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(from))))
            }
        }
        d.show()
    }

    private fun confirmDelete(sid: String) {
        AlertDialog.Builder(this)
            .setMessage("Delete this call record from your server?")
            .setPositiveButton("Delete") { _, _ -> bg({ client().deleteCall(sid) }) { toast("Deleted"); loadCalls() } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadKnowledge() {
        bg({ client().getKnowledge() }) { knowledge.setText(it) }
    }

    private fun saveKnowledge() {
        val text = knowledge.text.toString()
        if (text.length > 20_000) { toast("Too long (max 20,000 characters)"); return }
        bg({ client().putKnowledge(text) }) { toast("Saved — the assistant uses it from the next call") }
    }

    private fun dialCode(code: String, needsNumber: Boolean) {
        if (needsNumber && twilioNumber.isEmpty()) { toast("Connect to your server first"); return }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
    }

    private fun open(link: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } catch (e: Exception) { toast("No browser found") }
    }

    // ---------------- helpers ----------------
    private fun <T> bg(work: () -> T, done: (T) -> Unit) {
        Thread {
            try {
                val r = work()
                runOnUiThread { if (!isFinishing) done(r) }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "⚠️ ${e.message ?: e.javaClass.simpleName}"
                    toast(e.message ?: "Error")
                }
            }
        }.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun time(epochSec: Double): String =
        if (epochSec <= 0) "-" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date((epochSec * 1000).toLong()))

    private fun lang(code: String): String = when (code) {
        "ta" -> "Tamil"; "si" -> "Sinhala"; "en" -> "English"; else -> "-"
    }

    @Suppress("unused")
    private fun hide(v: View) { v.visibility = View.GONE }
}
