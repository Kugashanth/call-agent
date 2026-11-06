package lk.kathir.callagent.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import lk.kathir.callagent.AppLog
import lk.kathir.callagent.LocalStore
import lk.kathir.callagent.WavUtil
import java.io.File

// ======================= list =======================
@Composable
fun CallsScreen(tick: Int, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var calls by remember { mutableStateOf<List<CallRec>?>(null) }
    LaunchedEffect(tick) {
        calls = withContext(Dispatchers.IO) {
            val a = LocalStore.listCalls(ctx, 200)
            (0 until a.length()).map { CallRec.from(a.getJSONObject(it)) }
        }
    }
    val list = calls
    when {
        list == null -> Box(Modifier.fillMaxSize())
        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Call, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text("No calls yet", style = MaterialTheme.typography.titleLarge)
                Hint("Calls your agent answers will appear here with the caller's info, message, voice recording and script. Everything stays on this phone.")
            }
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.sid }) { c -> CallRow(c) { onOpen(c.sid) } }
        }
    }
}

@Composable
private fun CallRow(c: CallRec, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.who)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.who, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (c.hasRecording) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.PlayArrow, "Has recording", Modifier.size(16.dp), tint = cs.primary)
                    }
                }
                Text(c.message ?: "No message left", style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(langName(c.lang))
                    Text(whenText(c.started), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// ======================= detail =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(sid: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    var call by remember { mutableStateOf<CallRec?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(sid) {
        call = withContext(Dispatchers.IO) { runCatching { CallRec.from(LocalStore.getCall(ctx, sid)) }.getOrNull() }
    }
    val c = call
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Call details") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") } },
        )
        if (c == null) return@Column
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.who, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.who, style = MaterialTheme.typography.headlineSmall)
                    if (c.name != null && c.from.isNotEmpty()) Text(c.from, color = cs.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(langName(c.lang))
                        Pill(whenText(c.started), cs.surfaceContainerHigh, cs.onSurface)
                    }
                }
            }

            // actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.from.firstOrNull()?.let { it == '+' || it.isDigit() } == true) {
                    ActionButton(Icons.Filled.Call, "Call back", Modifier.weight(1f)) {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.from))))
                    }
                }
                ActionButton(Icons.Filled.Share, "Share script", Modifier.weight(1f)) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Call from ${c.who}")
                        .putExtra(Intent.EXTRA_TEXT, scriptText(c)), "Share script"))
                }
            }

            if (c.hasRecording) RecordingCard(sid, c.who)

            if (c.collected.isNotEmpty()) {
                SectionCard("Information collected", Icons.Filled.Info) {
                    c.collected.forEach { (k, v) ->
                        Row {
                            Text(k.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge,
                                color = cs.onSurfaceVariant, modifier = Modifier.width(120.dp))
                            Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (c.messages.isNotEmpty()) {
                SectionCard("Message for you", Icons.Filled.Star) {
                    c.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                }
            }

            if (c.turns.isNotEmpty()) {
                SectionCard("Script") {
                    c.turns.forEach { t ->
                        Bubble(t.caller, "Caller", mine = false)
                        Bubble(t.agent, "Agent", mine = true)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this call?") },
            text = { Text("The script, information and voice recording will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    LocalStore.deleteCall(ctx, sid); confirmDelete = false; onDeleted()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun Bubble(text: String, who: String, mine: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(who, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        Surface(
            color = if (mine) cs.primaryContainer else cs.surfaceContainerHighest,
            contentColor = if (mine) cs.onPrimaryContainer else cs.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun RecordingCard(sid: String, who: String) {
    val ctx = LocalContext.current
    val file = remember(sid) { LocalStore.recordingFile(ctx, sid) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var posSec by remember { mutableIntStateOf(0) }
    val total = remember(sid) { WavUtil.durationSec(file) }

    DisposableEffect(sid) {
        onDispose { try { player?.release() } catch (e: Exception) {}; player = null }
    }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p ->
                try {
                    val d = p.duration.coerceAtLeast(1)
                    progress = p.currentPosition.toFloat() / d
                    posSec = p.currentPosition / 1000
                } catch (e: Exception) {}
            }
            delay(200)
        }
    }

    fun stopPlayback() {
        try { player?.stop() } catch (e: Exception) {}
        player?.release(); player = null; playing = false; progress = 0f; posSec = 0
    }

    SectionCard("Voice recording", Icons.Filled.PlayArrow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = {
                    if (playing) { stopPlayback(); return@FilledIconButton }
                    if (!file.exists()) { toast(ctx, "Recording file missing"); return@FilledIconButton }
                    try {
                        player = MediaPlayer().apply {
                            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            setDataSource(file.absolutePath)
                            setOnCompletionListener { stopPlayback() }
                            prepare(); start()
                        }
                        playing = true
                    } catch (e: Exception) { toast(ctx, "Can't play: ${e.message}") }
                },
                modifier = Modifier.size(56.dp),
            ) { Icon(if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow, if (playing) "Stop" else "Play") }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                Spacer(Modifier.height(6.dp))
                Text("${fmt(posSec)} / ${fmt(total)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Icons.Filled.Share, "Share", Modifier.weight(1f)) { shareFile(ctx, file, "audio/wav", "Call with $who") }
            ActionButton(Icons.Filled.Done, "Save", Modifier.weight(1f)) { saveToDownloads(ctx, file, "call-$sid.wav") }
        }
        Hint("Whole conversation: caller and agent. Stored only on this phone.")
    }
}

private fun fmt(s: Int) = "%d:%02d".format(s / 60, s % 60)

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

fun shareFile(ctx: Context, file: File, mime: String, subject: String) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
    } catch (e: Exception) { toast(ctx, "Share failed: ${e.message}") }
}

/** Copies a file to Downloads/CallAgent (Android 10+, no storage permission needed). */
fun saveToDownloads(ctx: Context, file: File, name: String, mime: String = "audio/wav"): Boolean {
    if (Build.VERSION.SDK_INT < 29) { toast(ctx, "Saving to Downloads needs Android 10+. Use Share instead."); return false }
    return try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CallAgent")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        toast(ctx, "Saved to Downloads/CallAgent")
        true
    } catch (e: Exception) {
        AppLog.e("Save failed: $e"); toast(ctx, "Save failed: ${e.message}"); false
    }
}
