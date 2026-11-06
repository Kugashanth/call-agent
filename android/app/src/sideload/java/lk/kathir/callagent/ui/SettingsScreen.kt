package lk.kathir.callagent.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.kathir.callagent.AppLog
import lk.kathir.callagent.LocalStore
import lk.kathir.callagent.LocalVoice
import lk.kathir.callagent.Prefs
import java.io.File

@Composable
fun SettingsScreen(
    voice: LocalVoice,
    tick: Int,
    themeMode: Int, onTheme: (Int) -> Unit,
    dynamic: Boolean, onDynamic: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var key by remember { mutableStateOf(Prefs.geminiKey(ctx)) }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(Prefs.geminiModel(ctx)) }
    var delay by remember { mutableFloatStateOf(Prefs.delaySec(ctx).toFloat()) }
    var silence by remember { mutableFloatStateOf(Prefs.silenceMs(ctx).toFloat()) }
    var playback by remember { mutableIntStateOf(Prefs.playbackIndex(ctx)) }
    var record by remember { mutableStateOf(Prefs.recordCalls(ctx)) }
    var voiceStatus by remember { mutableStateOf("Checking…") }
    var storage by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf(AppLog.dump()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick, refresh) {
        withContext(Dispatchers.IO) {
            voiceStatus = listOf("ta" to "Tamil", "si" to "Sinhala", "en" to "English").joinToString("   ") { (c, n) ->
                (if (voice.hasVoice(c)) "✅ " else "❌ ") + n
            }
            val n = LocalStore.listCalls(ctx, 10_000).length()
            storage = "$n call(s) · %.1f MB on this phone".format(LocalStore.storageBytes(ctx) / 1_048_576.0)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- appearance ----------
        SectionCard("Appearance", Icons.Filled.Star) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                    FilterChip(
                        selected = themeMode == i, onClick = { onTheme(i) }, label = { Text(label) },
                        leadingIcon = if (themeMode == i) { { Icon(Icons.Filled.Check, null) } } else null,
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow("Use wallpaper colours", "Material You colours from your wallpaper", dynamic, onDynamic)
            }
        }

        // ---------- AI ----------
        SectionCard("Agent brain (Gemini)", Icons.Filled.Lock) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim(); Prefs.setStr(ctx, "gemini_key", it.trim()) },
                label = { Text("Gemini API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
            )
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
            }, modifier = Modifier.fillMaxWidth()) { Text("Get a free key (aistudio.google.com)") }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.trim(); Prefs.setStr(ctx, "gemini_model", it.trim()) },
                label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("gemini-flash-latest (default) or e.g. gemini-3.5-flash-lite for faster replies") },
            )
            Hint("Only the caller's audio for each turn is sent to Google Gemini to understand it. Everything else (recordings, scripts, info, settings) stays on this phone.")
        }

        // ---------- calls ----------
        SectionCard("Answering calls", Icons.Filled.Call) {
            Text("Pick up after ${delay.toInt()} s of ringing", style = MaterialTheme.typography.bodyLarge)
            Slider(value = delay, onValueChange = { delay = it }, valueRange = 0f..30f, steps = 29,
                onValueChangeFinished = { Prefs.setInt(ctx, "delay", delay.toInt()) })
            Text("Caller finished speaking after ${"%.1f".format(silence / 1000)} s of silence", style = MaterialTheme.typography.bodyLarge)
            Slider(value = silence, onValueChange = { silence = it }, valueRange = 500f..2500f, steps = 19,
                onValueChangeFinished = { Prefs.setInt(ctx, "silence_ms", silence.toInt()) })
            Text("Agent voice plays as", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Prefs.PLAYBACK_NAMES.forEachIndexed { i, n ->
                    FilterChip(selected = playback == i, onClick = { playback = i; Prefs.setInt(ctx, "playback", i) },
                        label = { Text(n) }, colors = FilterChipDefaults.filterChipColors())
                }
            }
            Hint("If callers can't hear the agent, try another option. Phones differ.")
            HorizontalDivider()
            SwitchRow("Record calls", "Saves the whole conversation as audio. Callers are told \"This call is recorded\".", record) {
                record = it; Prefs.setBool(ctx, "record", it)
            }
        }

        // ---------- voices ----------
        SectionCard("Voices", Icons.Filled.Settings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(voiceStatus, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, "Re-check") }
            }
            OutlinedButton(onClick = { openTtsSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Install Tamil / Sinhala voices")
            }
            Hint("Text-to-speech settings → Google engine ⚙ → Install voice data → Tamil and Sinhala.")
        }

        // ---------- setup ----------
        SectionCard("Phone setup", Icons.Filled.Build) {
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()) { Text("Accessibility service") }
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                catch (e: Exception) { toast(ctx, "Open Settings → Battery") }
            }, modifier = Modifier.fillMaxWidth()) { Text("Battery: don't restrict Call Agent") }
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }, modifier = Modifier.fillMaxWidth()) { Text("App info (permissions, restricted settings)") }
        }

        // ---------- data ----------
        SectionCard("Your data", Icons.Filled.Delete) {
            Text(storage, style = MaterialTheme.typography.bodyLarge)
            Hint("Calls, scripts, information and recordings are stored in this app's private storage. They are never backed up to the cloud, and are deleted if you uninstall.")
            OutlinedButton(onClick = { exportAll(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Export everything to Downloads/CallAgent")
            }
            OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete all calls & recordings", color = cs.error)
            }
        }

        // ---------- diagnostics ----------
        SectionCard("Diagnostics", Icons.Filled.Info) {
            SwitchRow("Show log", "What the agent heard and did", showLog) { showLog = it; logText = AppLog.dump() }
            if (showLog) {
                Row {
                    TextButton(onClick = { logText = AppLog.dump() }) { Text("Refresh") }
                    TextButton(onClick = { AppLog.clear(); logText = "" }) { Text("Clear") }
                }
                Text(logText.ifEmpty { "(empty)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        Hint("Call Agent Auto · everything runs on your phone except speech understanding (Gemini).")
        Spacer(Modifier.height(8.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("All calls, scripts, collected information and voice recordings will be permanently removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { LocalStore.deleteAll(ctx); confirmWipe = false; refresh++; toast(ctx, "Deleted") }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Writes every recording + one transcripts file to Downloads/CallAgent. */
private fun exportAll(ctx: android.content.Context) {
    Thread {
        try {
            val a = LocalStore.listCalls(ctx, 10_000)
            val all = StringBuilder()
            var recs = 0
            for (i in 0 until a.length()) {
                val c = CallRec.from(LocalStore.getCall(ctx, a.getJSONObject(i).getString("sid")))
                all.append(scriptText(c)).append("\n\n==============================\n\n")
                val f = LocalStore.recordingFile(ctx, c.sid)
                if (f.exists()) { saveToDownloadsQuiet(ctx, f, "call-${c.sid}.wav", "audio/wav"); recs++ }
            }
            val txt = File(ctx.cacheDir, "transcripts.txt").apply { writeText(all.toString()) }
            saveToDownloadsQuiet(ctx, txt, "CallAgent-transcripts-${System.currentTimeMillis() / 1000}.txt", "text/plain")
            txt.delete()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                toast(ctx, "Exported ${a.length()} call(s) and $recs recording(s) to Downloads/CallAgent")
            }
        } catch (e: Exception) {
            AppLog.e("Export failed: $e")
        }
    }.start()
}

private fun saveToDownloadsQuiet(ctx: android.content.Context, file: File, name: String, mime: String) {
    if (Build.VERSION.SDK_INT < 29) return
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/CallAgent")
    }
    val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
    ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
}
