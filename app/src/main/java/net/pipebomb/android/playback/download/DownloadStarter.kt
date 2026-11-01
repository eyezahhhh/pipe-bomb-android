package net.pipebomb.android.playback.download

import kotlinx.coroutines.flow.first
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.kindLabel
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.SessionState
import net.pipebomb.android.data.download.BulkEnqueueResult
import net.pipebomb.android.data.download.DownloadCollectionDraft
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.PlaybackResolver
import net.pipebomb.android.playback.ResolvedMedia
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CollectionStartResult {
    data class Started(val queued: Int, val skipped: Int) : CollectionStartResult
    data class Error(val message: String) : CollectionStartResult
}

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
    private val authRepository: AuthRepository,
    private val connectivity: ConnectivityStatus,
) {
    suspend fun start(track: Track): DownloadStartResult {
        val existing = repository.get(track.key)
        if (existing?.state in setOf(
                DownloadState.Completed.name,
                DownloadState.Queued.name,
                DownloadState.Downloading.name,
                DownloadState.Pending.name,
            )
        ) {
            AppLog.debug(
                category = LogCategory.DOWNLOAD,
                event = "download.enqueue.skipped",
                fields = mapOf(
                    "track" to track.key.toString(),
                    "state" to existing?.state,
                ),
            )
            return DownloadStartResult.AlreadyPresent
        }
        if (existing?.state == DownloadState.Unsupported.name) {
            return DownloadStartResult.Unsupported
        }
        preflightError()?.let { return it }
        return resolveAndQueue(track.key) { media ->
            repository.enqueue(track, media)
        }
    }

    suspend fun subscribeCollection(
        draft: DownloadCollectionDraft,
        tracks: List<Track>,
    ): CollectionStartResult {
        preflightError()?.let { return CollectionStartResult.Error(it.message) }
        val user = authRepository.session.value as? SessionState.Authenticated
        val result = repository.subscribeCollection(
            draft = draft,
            tracks = tracks,
            accountUuid = user?.user?.uuid,
            accountName = user?.user?.username,
        )
        scheduler.schedule()
        AppLog.info(
            category = LogCategory.DOWNLOAD,
            event = "download.collection.subscribe",
            fields = mapOf(
                "type" to draft.type.name,
                "queued" to result.queued,
                "skipped" to result.skipped,
                "tracks" to tracks.size,
            ),
        )
        return CollectionStartResult.Started(result.queued, result.skipped)
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
        if (!connectivity.isOnline.value) {
            AppLog.warn(
                category = LogCategory.DOWNLOAD,
                event = "download.preflight.failed",
                fields = mapOf("reason" to "offline"),
            )
            return DownloadStartResult.Error("Connect to download music")
        }
        if (!storage.resolve().downloadsEnabled || cache.storageState.isFallback) {
            AppLog.warn(
                category = LogCategory.DOWNLOAD,
                event = "download.preflight.failed",
                fields = mapOf(
                    "reason" to "storage",
                    "fallback" to cache.storageState.isFallback,
                ),
            )
            return DownloadStartResult.Error(
                "The selected storage is unavailable. Reconnect it or choose another location.",
            )
        }
        val reserve = settings.downloadReserveBytes.first()
        val freeBytes = storage.selectedAvailableBytes()
        return if (!DownloadSpace.canStartDownload(freeBytes, reserve)) {
            AppLog.warn(
                category = LogCategory.DOWNLOAD,
                event = "download.preflight.failed",
                fields = mapOf(
                    "reason" to "reserve",
                    "freeBytes" to freeBytes,
                    "reserveBytes" to reserve,
                ),
            )
            DownloadStartResult.Error("Not enough free space above your storage reserve.")
        } else {
            null
        }
    }

    private suspend fun resolveAndQueue(
        trackKey: TrackKey,
        enqueue: suspend (ResolvedMedia) -> Unit,
    ): DownloadStartResult = when (val result = resolver.resolve(trackKey, forceRefresh = true)) {
        is ApiResult.Failure -> {
            AppLog.warn(
                category = LogCategory.DOWNLOAD,
                event = "download.enqueue.failed",
                message = result.error.message,
                fields = mapOf(
                    "track" to trackKey.toString(),
                    "kind" to result.error.kindLabel(),
                ),
            )
            DownloadStartResult.Error(result.error.userMessage())
        }

        is ApiResult.Success -> when (result.value.type) {
            ResolvedMedia.MediaType.Hls -> {
                AppLog.warn(
                    category = LogCategory.DOWNLOAD,
                    event = "download.unsupported",
                    fields = mapOf("track" to trackKey.toString()),
                )
                DownloadStartResult.Unsupported
            }

            ResolvedMedia.MediaType.Stream -> {
                enqueue(result.value)
                scheduler.schedule()
                AppLog.info(
                    category = LogCategory.DOWNLOAD,
                    event = "download.enqueue",
                    fields = mapOf(
                        "track" to trackKey.toString(),
                        "mediaType" to "stream",
                    ),
                )
                DownloadStartResult.Started
            }
        }
    }
}
