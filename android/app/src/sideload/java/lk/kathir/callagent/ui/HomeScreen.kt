package lk.kathir.callagent.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.kathir.callagent.AppLog
import lk.kathir.callagent.LocalStore
import lk.kathir.callagent.LocalVoice
import lk.kathir.callagent.Prefs
import lk.kathir.callagent.StandaloneSession
import lk.kathir.callagent.UtteranceRecorder
import java.util.concurrent.atomic.AtomicBoolean

val CALL_PERMS: Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.ANSWER_PHONE_CALLS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun missingPerms(c: Context) = CALL_PERMS.filter { c.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

fun accessibilityOn(c: Context): Boolean {
    val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return list.contains("${c.packageName}/") && list.contains("CallAgentService")
}

data class Check(val ok: Boolean, val title: String, val fix: String?, val action: (() -> Unit)?)

@Composable
fun HomeScreen(voice: LocalVoice, tick: Int, onOpenCall: (String) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(Prefs.enabled(ctx)) }
    var voices by remember { mutableStateOf(Triple(true, true, true)) }
    var latest by remember { mutableStateOf<CallRec?>(null) }
    var count by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    LaunchedEffect(tick, refresh) {
        withContext(Dispatchers.IO) {
            voices = Triple(voice.hasVoice("ta"), voice.hasVoice("si"), voice.hasVoice("en"))
            val calls = LocalStore.listCalls(ctx, 50)
            count = calls.length()
            latest = if (calls.length() > 0) CallRec.from(calls.getJSONObject(0)) else null
        }
    }

    val missing = remember(tick, refresh) { missingPerms(ctx) }
    val a11y = remember(tick, refresh) { accessibilityOn(ctx) }
    val keySet = remember(tick, refresh) { Prefs.geminiKey(ctx).isNotEmpty() }
    val checks = listOf(
        Check(missing.isEmpty(), "Phone, microphone & notification permissions", "Allow") { permLauncher.launch(CALL_PERMS) },
        Check(a11y, "Accessibility service (lets the agent hear calls)", "Turn on") {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        Check(keySet, "Gemini API key (the agent's brain)", "Add") { onOpenSettings() },
        Check(voices.first, "Tamil voice installed", "Install") { openTtsSettings(ctx) },
        Check(voices.second, "Sinhala voice installed", "Install") { openTtsSettings(ctx) },
    )
    val ready = checks.all { it.ok }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- Hero ----------
        val cs = MaterialTheme.colorScheme
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${Prefs.ownerName(ctx)}'s AI call agent", color = cs.onPrimary,
                    style = MaterialTheme.typography.headlineSmall)
                Text(
                    when {
                        !ready -> "Finish setup below to start answering calls."
                        enabled -> "ON: answers calls you don't pick up after ${Prefs.delaySec(ctx)} s."
                        else -> "OFF: your calls ring normally."
                    },
                    color = cs.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) "Agent is ON" else "Agent is OFF", color = cs.onPrimary,
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on -> enabled = on; Prefs.setBool(ctx, "enabled", on) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = cs.primary, checkedTrackColor = cs.onPrimary,
                            uncheckedThumbColor = cs.onPrimary, uncheckedTrackColor = cs.onPrimary.copy(alpha = 0.3f),
                            uncheckedBorderColor = cs.onPrimary,
                        ),
                    )
                }
                Text("Tamil · Sinhala · English · Tanglish", color = cs.onPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium)
            }
        }

        // ---------- Setup ----------
        if (!ready) {
            SectionCard("Setup", Icons.Filled.Warning) {
                checks.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (c.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                            tint = if (c.ok) cs.primary else cs.error, modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(c.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (!c.ok && c.fix != null) TextButton(onClick = { c.action?.invoke() }) { Text(c.fix) }
                    }
                }
                if (!a11y && Build.VERSION.SDK_INT >= 33) {
                    Hint("Accessibility switch greyed out? Settings → Apps → Call Agent Auto → ⋮ → Allow restricted settings.")
                }
            }
        }

        // ---------- Try it ----------
        TestCard(voice)

        // ---------- Latest call ----------
        SectionCard("Latest call", Icons.Filled.Phone) {
            val l = latest
            if (l == null) {
                Hint("No calls yet. When the agent answers, the caller's details, message, voice recording and script appear here.")
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenCall(l.sid) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(l.who)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.who, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(l.message ?: "No message", style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(whenText(l.started), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Hint("$count call(s) saved on this phone")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TestCard(voice: LocalVoice) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    val stop = remember { AtomicBoolean(false) }
    val cs = MaterialTheme.colorScheme

    SectionCard("Talk to your agent", Icons.Filled.Face) {
        Hint("Test a full conversation through the speaker and mic, no call needed. Speak after the greeting; try Tanglish!")
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (running) { stop.set(true); return@Button }
                if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    AppLog.e("Microphone permission needed (Setup → Allow)"); return@Button
                }
                if (Prefs.geminiKey(ctx).isEmpty()) { AppLog.e("Add your Gemini key in Settings"); return@Button }
                stop.set(false); running = true
                Thread {
                    try {
                        val rec = UtteranceRecorder(MediaRecorder.AudioSource.MIC)
                        StandaloneSession(
                            ctx = ctx, voice = voice, usage = AudioAttributes.USAGE_MEDIA, from = "test", channel = "test",
                            record = { rec.record(shouldStop = { stop.get() }, silenceMs = Prefs.silenceMs(ctx)) },
                            shouldStop = { stop.get() },
                        ).run()
                    } catch (e: Exception) {
                        AppLog.e("Test failed: ${e.message}")
                    }
                    running = false
                }.start()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = if (running) ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError)
                     else ButtonDefaults.buttonColors(),
        ) {
            Icon(if (running) Icons.Filled.Close else Icons.Filled.Face, null)
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Stop" else "Start test conversation", fontWeight = FontWeight.Medium)
        }
        Hint("Problems? See Settings → Diagnostics log.")
    }
}

fun openTtsSettings(ctx: Context) {
    try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
    catch (e: Exception) {
        try { ctx.startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
        catch (x: Exception) { AppLog.e("Open Settings → Accessibility → Text-to-speech") }
    }
}
