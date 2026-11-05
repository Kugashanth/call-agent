package lk.kathir.callagent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
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

    // standalone
    private lateinit var standalone: Switch
    private lateinit var owner: EditText
    private lateinit var geminiKey: EditText
    private lateinit var model: EditText
    private lateinit var standaloneBox: LinearLayout
    // server
    private lateinit var url: EditText
    private lateinit var key: EditText
    private lateinit var serverBox: LinearLayout
    // common
    private lateinit var delay: EditText
    private lateinit var silence: EditText
    private lateinit var playback: Spinner
    private lateinit var enabled: Switch
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var testBtn: Button

    private var voice: LocalVoice? = null
    @Volatile private var testRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = LocalVoice(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad * 2) }

        fun box() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { col.addView(it) }
        fun heading(t: String, parent: LinearLayout = col) = TextView(this).apply {
            text = t; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setPadding(0, pad * 2, 0, pad / 3)
        }.also { parent.addView(it) }
        fun label(t: String, parent: LinearLayout = col) =
            TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }.also { parent.addView(it) }
        fun field(hint: String, value: String, type: Int, parent: LinearLayout = col) = EditText(this).apply {
            this.hint = hint; setText(value); inputType = type; isSingleLine = true
        }.also { parent.addView(it) }
        fun button(t: String, parent: LinearLayout = col, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }.also { parent.addView(it) }

        status = TextView(this).apply { textSize = 15f }.also { col.addView(it) }

        // ---------- Mode ----------
        heading("1. How the AI runs")
        standalone = Switch(this).apply {
            text = "Standalone: everything on this phone (no laptop/server)"
            isChecked = Prefs.standalone(this@MainActivity); setPadding(0, pad / 2, 0, pad / 2)
            setOnCheckedChangeListener { _, on -> showMode(on) }
        }.also { col.addView(it) }

        standaloneBox = box()
        label("Your name (the assistant says \"<name>'s AI assistant\")", standaloneBox)
        owner = field("Kathir", Prefs.ownerName(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, standaloneBox)
        label("Gemini API key (free: aistudio.google.com → Get API key)", standaloneBox)
        geminiKey = field("AIza…", Prefs.geminiKey(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, standaloneBox)
        button("Get a free Gemini key (opens browser)", standaloneBox) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }
        label("Gemini model", standaloneBox)
        model = field("gemini-flash-latest", Prefs.geminiModel(this), InputType.TYPE_CLASS_TEXT, standaloneBox)
        button("Install Tamil / Sinhala voices (Text-to-speech settings)", standaloneBox) { openTtsSettings() }

        serverBox = box()
        label("Server URL (https://… or http://192.168.x.x:8000 on Wi-Fi)", serverBox)
        url = field("https://...", Prefs.serverUrl(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, serverBox)
        label("Agent key (same as PHONE_AGENT_KEY on the server)", serverBox)
        key = field("secret key", Prefs.agentKey(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, serverBox)

        // ---------- Call settings ----------
        heading("2. Answering calls")
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
        button("💾 Save") { if (save()) toast("Saved") }

        // ---------- Setup ----------
        heading("3. Setup (once)")
        button("Grant permissions") { requestPermissions(perms, 1) }
        button("Turn on accessibility service") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        // ---------- Test ----------
        heading("4. Test without a call")
        testBtn = button("🎙 Talk to the assistant now (speaker + mic)") { toggleTest() }
        button("Test connection / voices") { if (save()) testSetup() }

        // ---------- Records ----------
        heading("Calls, messages & knowledge")
        button("📋 Open calls & edit what the AI may say") {
            if (save()) startActivity(Intent(this, DashboardActivity::class.java)
                .putExtra(DashboardActivity.EXTRA_LOCAL, Prefs.standalone(this)))
        }

        heading("Log (newest first)")
        button("Refresh log") { refresh() }
        log = TextView(this).apply { setTextIsSelectable(true); textSize = 12f; typeface = Typeface.MONOSPACE }
            .also { col.addView(it) }

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
        showMode(standalone.isChecked)
        if (!Prefs.consented(this)) showDisclosure()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        testRunning = false
        voice?.shutdown()
        super.onDestroy()
    }

    private fun showMode(standaloneOn: Boolean) {
        standaloneBox.visibility = if (standaloneOn) View.VISIBLE else View.GONE
        serverBox.visibility = if (standaloneOn) View.GONE else View.VISIBLE
    }

    /** Prominent disclosure + consent, shown before the app can do anything. */
    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Before you start")
            .setMessage(
                "Call Agent will:\n\n" +
                "• Automatically ANSWER incoming calls you don't pick up (after the delay you set).\n" +
                "• Use the MICROPHONE during those calls, through an Accessibility Service, to hear the caller.\n" +
                "• Send the caller's voice to Google Gemini using YOUR API key (standalone mode), or to YOUR OWN server " +
                "(server mode), to understand it and write a reply. Nothing is sent anywhere else.\n" +
                "• Tell every caller at the start that they are talking to an AI assistant.\n\n" +
                "Caller audio is not stored. Text transcripts and messages are saved privately on this phone " +
                "(or on your server) and you can delete them any time.\n\n" +
                "Laws on answering/recording calls differ by country — use this only for your own number and " +
                "tell regular callers you use an AI assistant.\n\n" +
                "You can turn everything off any time with the switch or in Accessibility settings."
            )
            .setCancelable(false)
            .setPositiveButton("I agree") { _, _ -> Prefs.setConsented(this, true); refresh() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun save(): Boolean {
        if (!Prefs.consented(this)) { enabled.isChecked = false; showDisclosure(); return false }
        val sa = standalone.isChecked
        if (sa && geminiKey.text.isBlank()) toast("Add your Gemini API key")
        val u = url.text.toString().trim().trimEnd('/')
        if (!sa && u.isNotEmpty() && !ServerClient.isAllowedUrl(u)) {
            toast("Use https:// (http:// only for LAN like 192.168.x.x)"); return false
        }
        Prefs.saveStandalone(this, sa, geminiKey.text.toString(), model.text.toString(), owner.text.toString())
        Prefs.save(
            this, u, key.text.toString(), enabled.isChecked,
            delay.text.toString().toIntOrNull() ?: 10,
            silence.text.toString().toIntOrNull() ?: 1000,
            playback.selectedItemPosition,
        )
        refresh()
        return true
    }

    private fun refresh() {
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        status.text = buildString {
            append(if (Prefs.standalone(this@MainActivity)) "📱 Standalone mode\n" else "🖥 Server mode\n")
            append(if (missing.isEmpty()) "✅ Permissions granted\n" else "❌ Missing: ${missing.joinToString { it.substringAfterLast('.') }}\n")
            append(if (isServiceOn()) "✅ Accessibility service ON\n" else "❌ Accessibility service OFF\n")
            if (Prefs.standalone(this@MainActivity) && Prefs.geminiKey(this@MainActivity).isEmpty()) append("❌ Gemini API key missing\n")
            append(if (Prefs.enabled(this@MainActivity)) "✅ Auto-answer enabled" else "⏸ Auto-answer disabled")
        }
        log.text = AppLog.dump()
    }

    private fun isServiceOn(): Boolean {
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.contains("$packageName/") && list.contains("CallAgentService")
    }

    private fun openTtsSettings() {
        try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
        catch (e: Exception) {
            try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            catch (x: Exception) { toast("Open Settings → Accessibility → Text-to-speech") }
        }
    }

    /** Checks Gemini key/server and which voices are installed. */
    private fun testSetup() {
        val v = voice ?: return
        val sa = Prefs.standalone(this)
        Thread {
            val lines = mutableListOf<String>()
            if (sa) {
                for ((code, name) in listOf("ta" to "Tamil", "si" to "Sinhala", "en" to "English")) {
                    lines += (if (v.hasVoice(code)) "✅ " else "❌ ") + "$name voice" +
                        (if (!v.hasVoice(code)) " (install in Text-to-speech settings)" else "")
                }
                lines += if (Prefs.geminiKey(this).isEmpty()) "❌ Gemini key missing" else "ℹ️ Gemini key set (checked on first test talk)"
            } else {
                lines += try { "✅ Server: " + ServerClient(Prefs.serverUrl(this), Prefs.agentKey(this)).health() }
                         catch (e: Exception) { "❌ Server: ${e.message}" }
            }
            lines.forEach { AppLog.i(it) }
            runOnUiThread {
                refresh()
                AlertDialog.Builder(this).setTitle("Setup check").setMessage(lines.joinToString("\n")).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    /** Full conversation through speaker + mic, without a phone call. */
    private fun toggleTest() {
        if (testRunning) { testRunning = false; testBtn.text = "Stopping…"; return }
        if (!save()) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(perms, 1); return
        }
        val v = voice ?: return
        testRunning = true
        testBtn.text = "⏹ Stop test"
        AppLog.i("Test conversation started — speak after the greeting")
        val usage = android.media.AudioAttributes.USAGE_MEDIA
        Thread {
            try {
                if (Prefs.standalone(this)) {
                    val rec = UtteranceRecorder(MediaRecorder.AudioSource.MIC)
                    StandaloneSession(
                        ctx = this, voice = v, usage = usage, from = "test", channel = "test",
                        record = { rec.record(shouldStop = { !testRunning }, silenceMs = Prefs.silenceMs(this)) },
                        shouldStop = { !testRunning },
                    ).run()
                } else {
                    val client = ServerClient(Prefs.serverUrl(this), Prefs.agentKey(this))
                    val id = "test-" + System.currentTimeMillis()
                    val r = client.start(id, "test")
                    r.audio?.let { Player(this).play(it, usage) }
                    client.end(id)
                    AppLog.i("Server greeting played (full conversations: use a real call)")
                }
            } catch (e: Exception) {
                AppLog.e("Test failed: ${e.message}")
            }
            testRunning = false
            runOnUiThread { testBtn.text = "🎙 Talk to the assistant now (speaker + mic)"; refresh() }
        }.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
