package dev.osianj.voxa.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.osianj.voxa.MainActivity
import dev.osianj.voxa.VoxaApplication
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Checks every feed twice a day (around 8am and 8pm) and posts a notification for new episodes.
 * Android decides the exact minute (battery saving), so treat the times as "around".
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as VoxaApplication).repository
        val recent = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
        var failures = 0
        for (show in repo.allShows()) {
            try {
                // Ignore very old episodes a feed might suddenly list, so we only announce real new ones.
                val fresh = repo.refresh(show.id).filter { it.publishedAt == 0L || it.publishedAt >= recent }
                if (fresh.isNotEmpty()) notifyNew(show, fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
            }
        }
        return if (failures > 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }

    private fun notifyNew(show: Show, fresh: List<ParsedEpisode>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "New episodes", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val newest = fresh.maxByOrNull { it.publishedAt } ?: fresh.first()
        val text = if (fresh.size == 1) newest.title else "${fresh.size} new episodes, latest: ${newest.title}"
        nm.notify(
            NOTIFICATION_BASE + show.id.toInt(),
            Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(show.title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_ID = "new_episodes"
        private const val NOTIFICATION_BASE = 2_000_000
        private const val WORK_NAME = "feed-refresh"

        /** Every 12 hours, first run at the next 8am or 8pm. KEEP so relaunching doesn't reset it. */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, 8)
                while (!after(now)) add(Calendar.HOUR_OF_DAY, 12)
                // Land on 8 or 20 only.
                if (get(Calendar.HOUR_OF_DAY) !in listOf(8, 20)) { set(Calendar.HOUR_OF_DAY, 20) }
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
