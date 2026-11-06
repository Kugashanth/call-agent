package lk.kathir.callagent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import lk.kathir.callagent.LocalVoice
import lk.kathir.callagent.Prefs

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Calls", Icons.Filled.Call),
    Tab("Agent", Icons.Filled.Face),
    Tab("Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentApp(
    voice: LocalVoice,
    tick: Int,
    openCall: String?,
    onCallOpened: () -> Unit,
    themeMode: Int, onTheme: (Int) -> Unit,
    dynamic: Boolean, onDynamic: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<String?>(null) }
    var localTick by remember { mutableIntStateOf(0) }
    var consented by remember { mutableStateOf(Prefs.consented(ctx)) }

    LaunchedEffect(openCall) {
        if (openCall != null) { detail = openCall; tab = 1; onCallOpened() }
    }
    val t = tick + localTick

    val d = detail
    if (d != null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            CallDetailScreen(d, onBack = { detail = null; localTick++ }, onDeleted = { detail = null; localTick++ })
        }
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (tab == 0) "Call Agent" else TABS[tab].title) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { i, tb ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i; localTick++ },
                            icon = { Icon(tb.icon, null) }, label = { Text(tb.title) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { current ->
                    when (current) {
                        0 -> HomeScreen(voice, t, onOpenCall = { detail = it }, onOpenSettings = { tab = 3 })
                        1 -> CallsScreen(t, onOpen = { detail = it })
                        2 -> AssistantScreen()
                        else -> SettingsScreen(voice, t, themeMode, onTheme, dynamic, onDynamic)
                    }
                }
            }
        }
    }

    if (!consented) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Before you start") },
            text = {
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Call Agent will:\n\n" +
                            "• Automatically ANSWER calls you don't pick up (after the delay you choose).\n" +
                            "• Use the MICROPHONE during those calls, through an Accessibility Service, to hear the caller.\n" +
                            "• Send each part of the caller's speech to Google Gemini with YOUR API key, only to understand it and write a reply.\n" +
                            "• Tell every caller it is an AI assistant (and that the call is recorded, if recording is on).\n\n" +
                            "Recordings, scripts, collected information and settings are stored ONLY on this phone, " +
                            "never backed up to the cloud, and you can delete them any time.\n\n" +
                            "Laws on answering and recording calls differ by country. Use this only for your own number."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { Prefs.setConsented(ctx, true); consented = true }) { Text("I agree") } },
            dismissButton = { TextButton(onClick = onExit) { Text("Exit") } },
        )
    }
}
