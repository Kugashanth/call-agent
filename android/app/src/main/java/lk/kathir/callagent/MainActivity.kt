package lk.kathir.callagent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings + status screen (built in code; no layout XML). */
class MainActivity : Activity() {

    private val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS,
    )

    private lateinit var url: EditText
    private lateinit var key: EditText
    private lateinit var delay: EditText
    private lateinit var silence: EditText
    private lateinit var playback: Spinner
    private lateinit var enabled: Switch
    private lateinit var status: TextView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }.also { col.addView(it) }
        fun field(hint: String, value: String, type: Int) = EditText(this).apply {
            this.hint = hint; setText(value); inputType = type; isSingleLine = true
        }.also { col.addView(it) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { onClick() }
        }.also { col.addView(it) }

        status = TextView(this).apply { textSize = 15f }.also { col.addView(it) }

        label("Server URL (e.g. https://xxxx.ngrok-free.app or http://192.168.1.10:8000)")
        url = field("https://...", Prefs.serverUrl(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        label("Agent key (same as PHONE_AGENT_KEY on the server)")
        key = field("secret key", Prefs.agentKey(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        label("Auto-answer after (seconds of ringing)")
        delay = field("10", Prefs.delaySec(this).toString(), InputType.TYPE_CLASS_NUMBER)
        label("End of speech = silence of (ms)")
        silence = field("1000", Prefs.silenceMs(this).toString(), InputType.TYPE_CLASS_NUMBER)
        label("AI voice plays as (try another if callers can't hear it)")
        playback = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Prefs.PLAYBACK_NAMES)
            setSelection(Prefs.playbackIndex(this@MainActivity))
        }.also { col.addView(it) }
        enabled = Switch(this).apply {
            text = "Auto-answer calls with AI"; isChecked = Prefs.enabled(this@MainActivity); setPadding(0, pad, 0, pad)
        }.also { col.addView(it) }

        button("💾 Save") { save(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        button("1. Grant permissions") { requestPermissions(perms, 1) }
        button("2. Turn on accessibility service") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("3. Test server connection") { save(); testServer() }
        button("4. Test AI voice (plays greeting)") { save(); testVoice() }
        button("Refresh log") { refresh() }

        label("Log (newest first)")
        log = TextView(this).apply { setTextIsSelectable(true); textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
            .also { col.addView(it) }

        setContentView(ScrollView(this).apply { addView(col) })
        if (!Prefs.consented(this)) showDisclosure()
    }

    /** Prominent disclosure + consent, shown before the app can do anything. */
    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Before you start")
            .setMessage(
                "Call Agent will:\n\n" +
                "• Automatically ANSWER incoming calls you don't pick up (after the delay you set).\n" +
                "• Use the MICROPHONE during those calls, through an Accessibility Service, to hear the caller.\n" +
                "• Send the caller's voice to YOUR OWN server (the URL you enter) for speech recognition and AI replies. " +
                "Nothing is sent anywhere else by this app.\n" +
                "• Tell every caller at the start that they are talking to an AI assistant.\n\n" +
                "Callers' voices are not stored by the app. Your server keeps text transcripts in its calls/ folder.\n\n" +
                "Laws on answering/recording calls differ by country — use this only for your own number and " +
                "tell regular callers you use an AI assistant.\n\n" +
                "You can turn everything off any time with the switch or in Accessibility settings."
            )
            .setCancelable(false)
            .setPositiveButton("I agree") { _, _ -> Prefs.setConsented(this, true); refresh() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun save() {
        if (!Prefs.consented(this)) { enabled.isChecked = false; showDisclosure(); return }
        if (url.text.isNotBlank() && !ServerClient.isAllowedUrl(url.text.toString().trim().trimEnd('/'))) {
            Toast.makeText(this, "Use https:// (http:// only for LAN like 192.168.x.x)", Toast.LENGTH_LONG).show()
        }
        Prefs.save(
            this, url.text.toString(), key.text.toString(), enabled.isChecked,
            delay.text.toString().toIntOrNull() ?: 10,
            silence.text.toString().toIntOrNull() ?: 1000,
            playback.selectedItemPosition,
        )
    }

    private fun refresh() {
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        val a11y = isServiceOn()
        status.text = buildString {
            append(if (missing.isEmpty()) "✅ Permissions granted\n" else "❌ Missing: ${missing.joinToString { it.substringAfterLast('.') }}\n")
            append(if (a11y) "✅ Accessibility service ON\n" else "❌ Accessibility service OFF (step 2)\n")
            append(if (Prefs.enabled(this@MainActivity)) "✅ Auto-answer enabled" else "⏸ Auto-answer disabled")
        }
        log.text = AppLog.dump()
    }

    private fun isServiceOn(): Boolean {
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.contains("$packageName/") && list.contains("CallAgentService")
    }

    private fun testServer() {
        val client = ServerClient(Prefs.serverUrl(this), Prefs.agentKey(this))
        Thread {
            val msg = try { client.health() } catch (e: Exception) { "Failed: $e" }
            AppLog.i("Server test: $msg")
            runOnUiThread { refresh(); Toast.makeText(this, msg.take(120), Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun testVoice() {
        val client = ServerClient(Prefs.serverUrl(this), Prefs.agentKey(this))
        val usage = Prefs.playbackUsage(this)
        Thread {
            try {
                val id = "test-" + System.currentTimeMillis()
                val r = client.start(id, "test")
                r.audio?.let { Player(this).play(it, usage) }
                client.end(id)
                AppLog.i("Voice test OK")
            } catch (e: Exception) {
                AppLog.e("Voice test failed: $e")
            }
            runOnUiThread { refresh() }
        }.start()
    }
}
