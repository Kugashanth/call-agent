package lk.kathir.callagent.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import lk.kathir.callagent.LocalStore
import lk.kathir.callagent.Phrases
import lk.kathir.callagent.Prefs

@Composable
fun AssistantScreen() {
    val ctx = LocalContext.current
    var owner by remember { mutableStateOf(Prefs.ownerName(ctx)) }
    var agent by remember { mutableStateOf(Prefs.agentName(ctx)) }
    val questions = remember {
        mutableStateListOf<String>().apply { addAll(Prefs.questions(ctx).lines().filter { it.isNotBlank() }) }
    }
    var newQ by remember { mutableStateOf("") }
    var knowledge by remember { mutableStateOf(LocalStore.knowledge(ctx)) }
    var savedKnowledge by remember { mutableStateOf(knowledge) }
    val cs = MaterialTheme.colorScheme

    fun saveQuestions() = Prefs.setStr(ctx, "questions", questions.joinToString("\n"))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard("Your agent", Icons.Filled.Face) {
            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it; Prefs.setStr(ctx, "owner", it.trim()) },
                label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = agent,
                onValueChange = { agent = it.take(30); Prefs.setStr(ctx, "agent_name", it.trim()) },
                label = { Text("Agent's name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("A polite call-centre agent who answers for you, e.g. Arun") },
            )
            Hint("Callers first hear the language menu:")
            Surface(color = cs.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    listOf("ta", "si", "en").joinToString("\n") { Phrases.languageMenu(it) } +
                        "\n\nThen, in their language: \"Thank you! I'm ${agent.ifBlank { "Arun" }}, " +
                        "${owner.ifBlank { "Kathir" }}'s AI assistant." +
                        (if (Prefs.recordCalls(ctx)) " This call is recorded." else "") + " How may I help you today?\"",
                    color = cs.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        SectionCard("Questions to ask every caller", Icons.Filled.Info) {
            Hint("The agent asks these naturally, in the caller's language, and lists the answers under \"Information collected\".")
            questions.forEachIndexed { i, q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = cs.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("${i + 1}", color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { questions.removeAt(i); saveQuestions() }) {
                        Icon(Icons.Filled.Close, "Remove", tint = cs.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newQ, onValueChange = { newQ = it.take(150) },
                    placeholder = { Text("e.g. Which company are you from?") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (newQ.isNotBlank() && questions.size < 10) { questions.add(newQ.trim()); newQ = ""; saveQuestions() }
                    },
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Filled.Add, "Add question") }
            }
        }

        SectionCard("What the agent may tell callers", Icons.Filled.Edit) {
            Hint("Your availability, services, FAQs. The agent answers ONLY from this text. Never put passwords or private details here.")
            OutlinedTextField(
                value = knowledge, onValueChange = { knowledge = it.take(20_000) },
                minLines = 10, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { LocalStore.setKnowledge(ctx, knowledge); savedKnowledge = knowledge; toast(ctx, "Saved: used from the next call") },
                enabled = knowledge != savedKnowledge,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (knowledge != savedKnowledge) "Save changes" else "Saved") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
