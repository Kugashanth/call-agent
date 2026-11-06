package lk.kathir.callagent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lk.kathir.callagent.ui.AgentApp
import lk.kathir.callagent.ui.AgentTheme

/** Entry point of the on-phone AI call agent (Jetpack Compose + Material 3, light/dark). */
class MainActivity : ComponentActivity() {

    private lateinit var voice: LocalVoice
    private var tick by mutableIntStateOf(0)                     // bumps on resume -> screens refresh
    private var openCall by mutableStateOf<String?>(null)        // from a notification tap
    private var themeMode by mutableIntStateOf(0)
    private var dynamic by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = LocalVoice(this)                                 // TTS must be created on the main thread
        Prefs.setBool(this, "standalone", true)                  // this app runs everything on the phone
        themeMode = Prefs.themeMode(this)
        dynamic = Prefs.dynamicColor(this)
        openCall = intent?.getStringExtra("open_call")

        setContent {
            AgentTheme(themeMode, dynamic) {
                AgentApp(
                    voice = voice,
                    tick = tick,
                    openCall = openCall,
                    onCallOpened = { openCall = null },
                    themeMode = themeMode,
                    onTheme = { themeMode = it; Prefs.setInt(this, "theme", it) },
                    dynamic = dynamic,
                    onDynamic = { dynamic = it; Prefs.setBool(this, "dynamic", it) },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open_call")?.let { openCall = it }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }
}
