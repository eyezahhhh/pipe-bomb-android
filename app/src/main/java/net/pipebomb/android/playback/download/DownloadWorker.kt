package net.pipebomb.android.playback.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import net.pipebomb.android.MainActivity
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.PlaybackResolver
import net.pipebomb.android.playback.ResolvedMedia

@HiltWorker
@OptIn(UnstableApi::class)
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: DownloadCoordinator,
    private val repository: DownloadRepository,
    private val storage: StorageLocationRepository,
    private val settings: AppSettingsStore,
    private val cache: PlaybackCache,
    private val resolver: PlaybackResolver,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ensureChannel()
        coordinator.awaitReady()

        if (!storage.resolve().downloadsEnabled || cache.storageState.isFallback) {
            coordinator.pauseUnavailable()
            repository.all()
                .filter { it.state == DownloadState.Queued.name }
                .forEach { repository.markPaused(it.trackKey, "Storage unavailable") }
            return Result.success()
        }

        val reserve = settings.downloadReserveBytes.first()
        if (!DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve)) {
            coordinator.pauseForReserve()
            return Result.success()
        }

        val queued = repository.all().filter { it.state == DownloadState.Queued.name }
        for (row in queued) {
            if (!DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve)) {
                coordinator.pauseForReserve()
                return Result.success()
            }
            val media = when (val result = resolver.resolve(row.key, forceRefresh = true)) {
                is ApiResult.Failure -> {
                    repository.markFailed(row.trackKey, result.error.userMessage())
                    continue
                }

                is ApiResult.Success -> result.value
            }
            if (media.type == ResolvedMedia.MediaType.Hls) {
                repository.markFailed(row.trackKey, "HLS downloads are not supported yet")
                continue
            }
            repository.updateSource(row.trackKey, media.sessionId, media.url)
            val request = DownloadRequest.Builder(row.trackKey, Uri.parse(media.url))
                .setCustomCacheKey("track:${row.trackKey}:stream")
                .build()
            coordinator.add(request)
        }

        while (true) {
            coordinator.syncProgress()
            val rows = repository.all()
            val active = rows.filter {
                it.state == DownloadState.Queued.name || it.state == DownloadState.Downloading.name
            }
            if (coordinator.reservePaused.value) return Result.success()
            if (!storage.resolve().downloadsEnabled || cache.storageState.isFallback) {
                coordinator.pauseUnavailable()
                active.forEach { repository.markPaused(it.trackKey, "Storage unavailable") }
                return Result.success()
            }
            if (!DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve)) {
                coordinator.pauseForReserve()
                return Result.success()
            }
            setForeground(foregroundInfo(rows))
            if (active.isEmpty() && coordinator.isIdle()) return Result.success()
            delay(1_000)
        }
    }

    private fun foregroundInfo(
        rows: List<net.pipebomb.android.data.download.DownloadEntity>,
    ): ForegroundInfo {
        val total = rows.count { it.state != DownloadState.Failed.name }.coerceAtLeast(1)
        val finished = rows.count { it.state == DownloadState.Completed.name }
        val bytes = rows.sumOf { it.bytesDownloaded }
        val text = if (rows.any { it.state == DownloadState.Downloading.name }) {
            "Downloading $finished of $total · ${formatBytes(bytes)}"
        } else {
            "Preparing downloads"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Pipe Bomb downloads")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(contentIntent())
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Music download progress"
                setShowBadge(false)
            },
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
        return String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2001
    }
}
