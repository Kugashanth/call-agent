package lk.kathir.callagent

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory log shown on the main screen (for debugging on the phone). */
object AppLog {
    private const val TAG = "CallAgent"
    private const val MAX = 300
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    private fun add(level: String, msg: String) {
        lines.addLast("${fmt.format(Date())} $level $msg")
        while (lines.size > MAX) lines.removeFirst()
    }

    fun i(msg: String) { Log.i(TAG, msg); add("I", msg) }
    fun e(msg: String) { Log.e(TAG, msg); add("E", msg) }

    @Synchronized
    fun dump(): String = lines.reversed().joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}
