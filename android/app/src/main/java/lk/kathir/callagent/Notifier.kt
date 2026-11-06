package lk.kathir.callagent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject

/** "Call answered by your AI agent" notifications (used by both app versions). */
object Notifier {
    private const val CHANNEL = "new_calls"

    fun callAnswered(c: Context, call: JSONObject) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Calls answered by AI", NotificationManager.IMPORTANCE_DEFAULT))
        val who = call.optString("caller_name").takeIf { it.isNotEmpty() && it != "null" } ?: call.optString("from", "Unknown caller")
        val msgs = call.optJSONArray("messages")
        val msg = if (msgs != null && msgs.length() > 0) msgs.optString(msgs.length() - 1) else "Tap to see the script and recording"
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra("open_call", call.optString("sid"))
        val pi = launch?.let {
            PendingIntent.getActivity(c, call.optString("sid").hashCode(), it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val b = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle("📞 $who — answered by your AI agent")
            .setContentText(msg)
            .setStyle(Notification.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
        if (pi != null) b.setContentIntent(pi)
        nm.notify(call.optString("sid").hashCode(), b.build())
    }
}
