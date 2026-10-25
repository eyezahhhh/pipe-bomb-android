package net.pipebomb.android.playback.download

import kotlinx.coroutines.flow.first
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.PlaybackResolver
import net.pipebomb.android.playback.ResolvedMedia
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadStartResult {
    data object Started : DownloadStartResult
    data object Unsupported : DownloadStartResult
    data object AlreadyPresent : DownloadStartResult
    data class Error(val message: String) : DownloadStartResult
}

@Singleton
class DownloadStarter @Inject constructor(
    private val repository: DownloadRepository,
    private val resolver: PlaybackResolver,
    private val scheduler: DownloadScheduler,
    private val storage: StorageLocationRepository,
    private val settings: AppSettingsStore,
    private val cache: PlaybackCache,
) {
    suspend fun start(track: Track): DownloadStartResult {
        val existing = repository.get(track.key)
        if (existing?.state in setOf(
                DownloadState.Completed.name,
                DownloadState.Queued.name,
                DownloadState.Downloading.name,
            )
        ) {
            return DownloadStartResult.AlreadyPresent
        }
        preflightError()?.let { return it }
        return resolveAndQueue(track.key) { media ->
            repository.enqueue(track, media)
        }
    }

    suspend fun retry(trackKey: TrackKey): DownloadStartResult {
        if (repository.get(trackKey) == null) {
            return DownloadStartResult.Error("Download not found")
        }
        preflightError()?.let { return it }
        return resolveAndQueue(trackKey) { media ->
            repository.requeue(trackKey, media)
        }
    }

    private suspend fun preflightError(): DownloadStartResult.Error? {
        if (!storage.resolve().downloadsEnabled || cache.storageState.isFallback) {
            return DownloadStartResult.Error(
                "The selected storage is unavailable. Reconnect it or choose another location.",
            )
        }
        val reserve = settings.downloadReserveBytes.first()
        return if (!DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve)) {
            DownloadStartResult.Error("Not enough free space above your storage reserve.")
        } else {
            null
        }
    }

    private suspend fun resolveAndQueue(
        trackKey: TrackKey,
        enqueue: suspend (ResolvedMedia) -> Unit,
    ): DownloadStartResult = when (val result = resolver.resolve(trackKey, forceRefresh = true)) {
        is ApiResult.Failure -> DownloadStartResult.Error(result.error.userMessage())
        is ApiResult.Success -> when (result.value.type) {
            ResolvedMedia.MediaType.Hls -> DownloadStartResult.Unsupported
            ResolvedMedia.MediaType.Stream -> {
                enqueue(result.value)
                scheduler.schedule()
                DownloadStartResult.Started
            }
        }
    }
}
