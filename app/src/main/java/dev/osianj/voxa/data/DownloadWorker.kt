package dev.osianj.voxa.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.osianj.voxa.VoxaApplication
import java.util.concurrent.TimeUnit

/**
 * Background downloads with an ongoing notification (progress bar + Cancel).
 * Two modes: a whole show (newest 5 not yet downloaded) or a single episode.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val showId get() = inputData.getLong(KEY_SHOW_ID, -1)
    private val episodeId get() = inputData.getLong(KEY_EPISODE_ID, -1)
    private val notificationId
        get() = if (episodeId >= 0) NOTIFICATION_ID_BASE + 100_000 + episodeId.toInt()
        else NOTIFICATION_ID_BASE + showId.toInt()

    override suspend fun doWork(): Result {
        if (showId < 0) return Result.failure()
        val repo = (applicationContext as VoxaApplication).repository
        val showTitle = repo.getShow(showId)?.title ?: "podcast"

        setForeground(foregroundInfo(showTitle, "Starting…", indeterminate = true, percent = 0))

        return try {
            var label = ""
            if (episodeId >= 0) {
                val ep = repo.getEpisode(episodeId) ?: return Result.failure()
                label = ep.title
                notify(showTitle, label, indeterminate = true, percent = 0)
                repo.downloadEpisode(ep) { f ->
                    notify(showTitle, label, indeterminate = false, percent = (f * 100).toInt())
                }
            } else {
                val failed = repo.downloadScope(
                    showId = showId,
                    scope = DownloadScope.decode(inputData.getString(KEY_SCOPE)),
                    oldestFirst = inputData.getBoolean(KEY_OLDEST_FIRST, false),
                    onEpisodeStart = { ep, index, total ->
                        label = "${ep.title} ($index of $total)"
                        notify(showTitle, label, indeterminate = true, percent = 0)
                    },
                    onProgress = { f -> notify(showTitle, label, indeterminate = false, percent = (f * 100).toInt()) },
                )
                if (failed > 0) notifyResult(showTitle, "$failed episode(s) failed to download")
            }
            notificationManager.cancel(notificationId)
            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            notificationManager.cancel(notificationId)
            throw e
        } catch (e: Exception) {
            if (runAttemptCount >= MAX_ATTEMPTS) {
                notifyResult(showTitle, "Download failed")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    /** A separate, dismissable notification: the progress one is removed when the worker stops. */
    private fun notifyResult(title: String, text: String) {
        ensureChannel(applicationContext)
        notificationManager.notify(
            notificationId + 1_000_000,
            Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notify(title: String, text: String, indeterminate: Boolean, percent: Int, ongoing: Boolean = true) {
        notificationManager.notify(notificationId, buildNotification(title, text, indeterminate, percent, ongoing))
    }

    private fun foregroundInfo(title: String, text: String, indeterminate: Boolean, percent: Int) =
        ForegroundInfo(
            notificationId,
            buildNotification(title, text, indeterminate, percent, ongoing = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    private fun buildNotification(
        title: String,
        text: String,
        indeterminate: Boolean,
        percent: Int,
        ongoing: Boolean,
    ): Notification {
        ensureChannel(applicationContext)
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        return Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .addAction(Notification.Action.Builder(null, "Cancel", cancelIntent).build())
            .build()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val KEY_SHOW_ID = "showId"
        private const val KEY_EPISODE_ID = "episodeId"
        private const val KEY_SCOPE = "scope"
        private const val KEY_OLDEST_FIRST = "oldestFirst"
        private const val NOTIFICATION_ID_BASE = 1000
        private const val MAX_ATTEMPTS = 3

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        /** Bulk download for a show. [oldestFirst] mirrors the show's sort order. */
        fun enqueue(context: Context, showId: Long, scope: DownloadScope, oldestFirst: Boolean) = enqueue(
            context,
            "download-$showId",
            workDataOf(KEY_SHOW_ID to showId, KEY_SCOPE to scope.encode(), KEY_OLDEST_FIRST to oldestFirst),
        )

        /** One specific episode. */
        fun enqueueEpisode(context: Context, showId: Long, episodeId: Long) = enqueue(
            context, "download-ep-$episodeId", workDataOf(KEY_SHOW_ID to showId, KEY_EPISODE_ID to episodeId)
        )

        private fun enqueue(context: Context, name: String, data: androidx.work.Data) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        }
    }
}
