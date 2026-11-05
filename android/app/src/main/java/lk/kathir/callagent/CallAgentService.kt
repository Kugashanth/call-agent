package lk.kathir.callagent

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/**
 * Core of the app. Runs as an AccessibilityService because Android only lets accessibility services
 * use the microphone while a phone call is active (ordinary apps receive silence).
 *
 * Ringing -> wait N seconds -> if you didn't pick up, auto-answer -> speakerphone on ->
 * loop { play AI voice -> record caller -> send to server } -> hang up.
 */
class CallAgentService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var pendingAnswer: Runnable? = null
    private var autoAnswered = false
    private var incomingNumber: String? = null

    @Volatile private var session: Thread? = null
    @Volatile private var stopFlag = false
    private var savedVolume = -1
    private var savedStream = AudioManager.STREAM_MUSIC

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val telecom by lazy { getSystemService(Context.TELECOM_SERVICE) as TelecomManager }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onPhoneState(i)
        }
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(r, filter)
        receiver = r
        AppLog.i("Accessibility service connected — ready to answer calls")
    }

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        stopSession()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---------------- call state ----------------
    @Suppress("DEPRECATION")
    private fun onPhoneState(i: Intent) {
        val state = i.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.let { if (it.isNotEmpty()) incomingNumber = it }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!Prefs.consented(this) || !Prefs.enabled(this) || pendingAnswer != null || session != null) return
                val delay = Prefs.delaySec(this)
                val r = Runnable { pendingAnswer = null; answer() }
                pendingAnswer = r
                main.postDelayed(r, delay * 1000L)
                AppLog.i("Ringing (${mask(incomingNumber)}) — auto-answer in ${delay}s if you don't pick up")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                pendingAnswer?.let {
                    main.removeCallbacks(it); pendingAnswer = null
                    AppLog.i("You answered the call yourself — agent stays off")
                }
                if (autoAnswered && session == null) startSession(incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                pendingAnswer?.let { main.removeCallbacks(it); pendingAnswer = null }
                if (session != null) AppLog.i("Call ended")
                stopSession()
                autoAnswered = false
                incomingNumber = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun answer() {
        try {
            autoAnswered = true
            telecom.acceptRingingCall()
            AppLog.i("Auto-answered")
        } catch (e: Exception) {
            autoAnswered = false
            AppLog.e("Auto-answer failed: $e (grant 'Answer calls' permission)")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun hangUp() {
        try { telecom.endCall() } catch (e: Exception) { AppLog.e("Hang up failed: $e") }
    }

    // ---------------- conversation ----------------
    private fun startSession(number: String?) {
        stopFlag = false
        session = Thread({ runSession(number) }, "call-session").also { it.start() }
    }

    private fun stopSession() {
        stopFlag = true
        session?.interrupt()
        session = null
    }

    private fun runSession(number: String?) {
        val id = UUID.randomUUID().toString()
        val client = ServerClient(Prefs.serverUrl(this), Prefs.agentKey(this))
        val player = Player(this)
        val usage = Prefs.playbackUsage(this)
        val recorder = UtteranceRecorder(MediaRecorder.AudioSource.MIC)
        val silenceMs = Prefs.silenceMs(this)
        try {
            Thread.sleep(1200)                     // let the call audio connect
            main.post { enableSpeaker() }
            boostVolume(Prefs.playbackStream(this))
            Thread.sleep(600)

            var r = client.start(id, number)
            r.audio?.let { player.play(it, usage) }
            var turns = 0
            while (!stopFlag && !r.endCall && turns < 30) {
                val wav = recorder.record(shouldStop = { stopFlag }, silenceMs = silenceMs)
                if (stopFlag) break
                r = client.turn(id, wav ?: ByteArray(0))
                AppLog.i("Caller: ${r.heard.ifEmpty { "(silence)" }}\nAgent [${r.lang}]: ${r.reply}")
                if (stopFlag) break
                r.audio?.let { player.play(it, usage) }
                turns++
            }
            if (r.endCall && !stopFlag) {
                Thread.sleep(400)
                hangUp()
            }
        } catch (e: InterruptedException) {
            // call ended by caller
        } catch (e: Exception) {
            AppLog.e("Session error: $e")
            if (!stopFlag) hangUp()
        } finally {
            try { client.end(id) } catch (e: Exception) {}
            restoreVolume()
        }
    }

    private fun mask(n: String?): String =
        if (n != null && n.length > 7) n.take(4) + "****" + n.takeLast(3) else (n ?: "unknown")

    // ---------------- audio routing ----------------
    @Suppress("DEPRECATION")
    private fun enableSpeaker() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audio.setCommunicationDevice(it) }
            }
            audio.isSpeakerphoneOn = true
        } catch (e: Exception) {
            AppLog.e("Speaker API failed: $e")
        }
        // Many phones ignore the API for normal calls, so also press the dialer's Speaker button.
        main.postDelayed({ if (!clickSpeakerButton()) AppLog.i("Speaker button not found — check speaker is on") }, 700)
    }

    private fun clickSpeakerButton(): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try { windows.mapNotNullTo(roots) { it.root } } catch (e: Exception) {}

        val labels = listOf("Speaker", "Speakerphone", "Loudspeaker", "ஒலிபெருக்கி", "ස්පීකරය")
        for (root in roots) for (label in labels) {
            for (node in root.findAccessibilityNodeInfosByText(label)) {
                if (node.isCheckable && node.isChecked) { AppLog.i("Speaker already on"); return true }
                var n: AccessibilityNodeInfo? = node
                while (n != null && !n.isClickable) n = n.parent
                if (n != null && n.isCheckable && n.isChecked) { AppLog.i("Speaker already on"); return true }
                if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    AppLog.i("Pressed in-call Speaker button")
                    return true
                }
            }
        }
        return false
    }

    private fun boostVolume(stream: Int) {
        try {
            savedStream = stream
            savedVolume = audio.getStreamVolume(stream)
            audio.setStreamVolume(stream, audio.getStreamMaxVolume(stream), 0)
        } catch (e: Exception) {
            savedVolume = -1
        }
    }

    private fun restoreVolume() {
        if (savedVolume < 0) return
        try { audio.setStreamVolume(savedStream, savedVolume, 0) } catch (e: Exception) {}
        savedVolume = -1
    }
}
