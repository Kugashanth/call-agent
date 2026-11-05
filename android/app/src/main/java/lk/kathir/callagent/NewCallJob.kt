package lk.kathir.callagent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.Notification

/**
 * Checks your server for newly answered calls (every ~15 min, Android's minimum) and shows a notification
 * with the caller and their message. Uses only the framework JobScheduler — no Google services needed.
 */
class NewCallJob : JobService() {

    companion object {
        private const val JOB_ID = 4242
        private const val CHANNEL = "new_calls"

        fun schedule(c: Context) {
            val url = Prefs.serverUrl(c)
            val js = c.getSystemService(JobScheduler::class.java)
            if (url.isEmpty() || url == ServerClient.DEMO || url == ServerClient.LOCAL) { js.cancel(JOB_ID); return }
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(c, NewCallJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build()
            )
        }

        /** Shows notifications for calls newer than the last one seen. Background thread only. */
        fun check(c: Context) {
            val calls = ServerClient(Prefs.serverUrl(c), Prefs.agentKey(c), c).listCalls(10)
            val last = Prefs.lastSeen(c)
            var newest = last
            val fresh = (0 until calls.length()).map { calls.getJSONObject(it) }
                .filter { it.optDouble("started", 0.0).toLong() > last }
            for (call in fresh) newest = maxOf(newest, call.optDouble("started", 0.0).toLong())
            if (last == 0L) { Prefs.setLastSeen(c, newest); return }   // first run: don't flood old calls
            fresh.take(5).forEach { notify(c, it) }
            if (newest > last) Prefs.setLastSeen(c, newest)
        }

        private fun notify(c: Context, call: org.json.JSONObject) {
            if (Build.VERSION.SDK_INT >= 33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Calls answered by AI", NotificationManager.IMPORTANCE_DEFAULT))
            val who = call.optString("caller_name").takeIf { it.isNotEmpty() && it != "null" } ?: call.optString("from", "Unknown caller")
            val msgs = call.optJSONArray("messages")
            val msg = if (msgs != null && msgs.length() > 0) msgs.optString(msgs.length() - 1) else "Tap to see the script and recording"
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_missed_call)
                .setContentTitle("📞 $who — answered by your AI agent")
                .setContentText(msg)
                .setStyle(Notification.BigTextStyle().bigText(msg))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(call.optString("sid").hashCode(), n)
        }
    }

    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        worker = Thread {
            try { check(this) } catch (e: Exception) { AppLog.e("New-call check failed: ${e.message}") }
            jobFinished(params, false)
        }.also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        worker?.interrupt()
        return true
    }
}
