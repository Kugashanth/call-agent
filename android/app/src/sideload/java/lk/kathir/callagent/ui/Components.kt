package lk.kathir.callagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- model ----------------
data class Turn(val caller: String, val agent: String)

data class CallRec(
    val sid: String,
    val from: String,
    val name: String?,
    val started: Double,
    val ended: Double,
    val lang: String,
    val messages: List<String>,
    val collected: List<Pair<String, String>>,
    val turns: List<Turn>,
    val hasRecording: Boolean,
) {
    val who: String get() = name ?: from.ifEmpty { "Unknown caller" }
    val message: String? get() = messages.lastOrNull()

    companion object {
        fun from(j: JSONObject): CallRec {
            val msgs = j.optJSONArray("messages")
            val col = j.optJSONObject("collected")
            val turnsArr = j.optJSONArray("turns")
            return CallRec(
                sid = j.optString("sid"),
                from = j.optString("from", ""),
                name = j.optString("caller_name", "").takeIf { it.isNotBlank() && it != "null" },
                started = j.optDouble("started", 0.0),
                ended = j.optDouble("ended", 0.0),
                lang = j.optString("lang", "en"),
                messages = if (msgs == null) emptyList() else (0 until msgs.length()).map { msgs.optString(it) },
                collected = if (col == null) emptyList() else col.keys().asSequence().map { it to col.optString(it) }.toList(),
                turns = if (turnsArr == null) emptyList() else (0 until turnsArr.length()).mapNotNull { i ->
                    turnsArr.optJSONObject(i)?.let { Turn(it.optString("caller"), it.optString("agent")) }
                },
                hasRecording = j.optBoolean("has_recording", false),
            )
        }
    }
}

fun langName(code: String) = when (code) { "ta" -> "Tamil"; "si" -> "Sinhala"; else -> "English" }

fun whenText(epochSec: Double): String {
    if (epochSec <= 0) return "-"
    val d = Date((epochSec * 1000).toLong())
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return if (SimpleDateFormat("yyyyMMdd", Locale.US).format(d) == today)
        "Today, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(d)
    else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(d)
}

fun scriptText(c: CallRec): String = buildString {
    append("Call from ${c.from.ifEmpty { "Unknown" }} · ${whenText(c.started)} · ${langName(c.lang)}\n\n")
    if (c.collected.isNotEmpty()) {
        append("INFORMATION COLLECTED\n")
        c.collected.forEach { (k, v) -> append("• ${k.replaceFirstChar { it.uppercase() }}: $v\n") }
        append("\n")
    }
    if (c.messages.isNotEmpty()) {
        append("MESSAGE\n"); c.messages.forEach { append("• $it\n") }; append("\n")
    }
    if (c.turns.isNotEmpty()) {
        append("SCRIPT\n")
        c.turns.forEach { append("Caller: ${it.caller}\nAgent: ${it.agent}\n\n") }
    }
}.trim()

// ---------------- components ----------------
private val avatarColors = listOf(
    Color(0xFF0F766E), Color(0xFF4458A8), Color(0xFFA16207), Color(0xFF9D3D6B), Color(0xFF2E7D32), Color(0xFF6D4C9F),
)

@Composable
fun Avatar(text: String, size: Dp = 44.dp) {
    val initial = text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    val bg = avatarColors[(text.hashCode() and 0x7fffffff) % avatarColors.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
            content()
        }
    }
}

@Composable
fun Pill(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer,
         content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = container, shape = RoundedCornerShape(50)) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
