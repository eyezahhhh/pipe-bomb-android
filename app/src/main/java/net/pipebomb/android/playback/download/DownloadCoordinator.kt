package net.pipebomb.android.playback.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.di.ApplicationScope
import net.pipebomb.android.playback.PlaybackCache
import javax.inject.Inject
import javax.inject.Singleton

private data class DownloadNetworkState(
    val allowMobileData: Boolean,
    val isOnline: Boolean,
    val isMetered: Boolean,
    val storageRevision: Long,
)

@Singleton
@OptIn(UnstableApi::class)
class DownloadCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val cache: PlaybackCache,
    private val databaseProvider: StandaloneDatabaseProvider,
    private val repository: DownloadRepository,
    private val storage: StorageLocationRepository,
    private val settings: AppSettingsStore,
    private val connectivity: ConnectivityStatus,
    private val scheduler: DownloadScheduler,
) {
    private val initialized = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<Unit>()
    private val lastStates = mutableMapOf<String, Int>()
    private var lastAvailabilitySignature: String? = null
    private val _reservePaused = MutableStateFlow(false)
    val reservePaused: StateFlow<Boolean> = _reservePaused.asStateFlow()
    private val _mobileDataPaused = MutableStateFlow(false)
    val mobileDataPaused: StateFlow<Boolean> = _mobileDataPaused.asStateFlow()
    private val _downloadsUnavailable = MutableStateFlow<String?>(null)
    val downloadsUnavailable: StateFlow<String?> = _downloadsUnavailable.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var manager: DownloadManager? = null

    fun start() {
        if (started) return
        started = true
        AppLog.info(
            category = LogCategory.DOWNLOAD,
            event = "download.manager.start",
            fields = mapOf("streamMaxBytes" to PlaybackCache.MAX_STREAM_BYTES),
        )
        val downloadManager = runCatching {
            DownloadManager(
                context,
                databaseProvider,
                cache.downloadCache,
                DefaultHttpDataSource.Factory(),
                Runnable::run,
            )
        }.getOrElse { error ->
            AppLog.error(
                category = LogCategory.DOWNLOAD,
                event = "download.manager.start",
                message = error.message,
                fields = mapOf("exception" to error.javaClass.simpleName),
            )
            _downloadsUnavailable.value = "Download storage is unavailable. Restart Pipe Bomb."
            ready.complete(Unit)
            return
        }
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) {
                initialized.complete(Unit)
            }

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                val trackKey = download.request.id
                val previousState = synchronized(lastStates) {
                    lastStates.put(trackKey, download.state)
                }
                if (previousState != download.state || finalException != null) {
                    if (download.state == Download.STATE_FAILED || finalException != null) {
                        AppLog.error(
                            category = LogCategory.DOWNLOAD,
                            event = "download.failed",
                            message = finalException?.message,
                            fields = mapOf(
                                "track" to trackKey,
                                "state" to download.stateName(),
                                "exception" to finalException?.javaClass?.simpleName,
                                "bytes" to download.bytesDownloaded,
                                "totalBytes" to download.contentLength,
                            ),
                        )
                    } else {
                        AppLog.info(
                            category = LogCategory.DOWNLOAD,
                            event = "download.state",
                            fields = mapOf(
                                "track" to trackKey,
                                "from" to previousState?.let(::downloadStateName),
                                "to" to download.stateName(),
                                "bytes" to download.bytesDownloaded,
                                "totalBytes" to download.contentLength,
                            ),
                        )
                    }
                }
                scope.launch(Dispatchers.IO) {
                    repository.updateProgress(
                        trackKey = download.request.id,
                        bytesDownloaded = download.bytesDownloaded,
                        bytesTotal = if (download.contentLength > 0) download.contentLength else 0,
                        state = download.state.toDownloadState(),
                        error = finalException?.let { "Download failed" },
                    )
                }
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) {
                synchronized(lastStates) { lastStates.remove(download.request.id) }
                AppLog.info(
                    category = LogCategory.DOWNLOAD,
                    event = "download.removed",
                    fields = mapOf(
                        "track" to download.request.id,
                        "bytes" to download.bytesDownloaded,
                    ),
                )
                scope.launch(Dispatchers.IO) {
                    repository.delete(download.request.id)
                    cache.removeTrack(download.request.id)
                }
            }
        })
        manager = downloadManager

        scope.launch {
            try {
                val initializedResult = withTimeoutOrNull(START_TIMEOUT_MS) {
                    initialized.await()
                }
                if (initializedResult == null) {
                    AppLog.error(
                        category = LogCategory.DOWNLOAD,
                        event = "download.manager.start",
                        message = "timed out waiting for DownloadManager",
                    )
                    _downloadsUnavailable.value =
                        "Download manager did not start. Restart Pipe Bomb."
                    manager = null
                    ready.complete(Unit)
                    return@launch
                }
                val rows = repository.all()
                reconcile(downloadManager, rows)
                ready.complete(Unit)
                scope.launch {
                    combine(
                        settings.allowMobileDataDownloads,
                        connectivity.isOnline,
                        connectivity.isMetered,
                        storage.revision,
                    ) { allowMobileData, isOnline, isMetered, storageRevision ->
                        DownloadNetworkState(
                            allowMobileData = allowMobileData,
                            isOnline = isOnline,
                            isMetered = isMetered,
                            storageRevision = storageRevision,
                        )
                    }
                        .distinctUntilChanged()
                        .collect {
                            refreshAvailability()
                            ensureDownloadsScheduled()
                        }
                }
                refreshAvailability()
                ensureDownloadsScheduled()
                scheduler.scheduleWatchdog()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.error(
                    category = LogCategory.DOWNLOAD,
                    event = "download.manager.start",
                    message = error.message,
                    fields = mapOf("exception" to error.javaClass.simpleName),
                )
                _downloadsUnavailable.value =
                    "Download manager did not start. Restart Pipe Bomb."
                manager = null
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() {
        start()
        ready.await()
    }

    suspend fun add(download: androidx.media3.exoplayer.offline.DownloadRequest) {
        awaitReady()
        AppLog.debug(
            category = LogCategory.DOWNLOAD,
            event = "download.add",
            fields = mapOf("track" to download.id),
        )
        withContext(Dispatchers.Main) {
            manager?.addDownload(download)
        }
    }

    suspend fun remove(trackKey: String) {
        awaitReady()
        AppLog.info(
            category = LogCategory.DOWNLOAD,
            event = "download.remove",
            fields = mapOf("track" to trackKey),
        )
        withContext(Dispatchers.Main) {
            manager?.removeDownload(trackKey)
        }
    }

    suspend fun removeAll() {
        awaitReady()
        AppLog.info(category = LogCategory.DOWNLOAD, event = "download.removeAll")
        withContext(Dispatchers.Main) {
            manager?.removeAllDownloads()
        }
    }

    suspend fun pauseForReserve() {
        _reservePaused.value = true
        pauseAll()
    }

    suspend fun pauseForMobileData() {
        _mobileDataPaused.value = true
        pauseAll()
    }

    suspend fun pauseAll() {
        withContext(Dispatchers.Main) {
            manager?.pauseDownloads()
        }
    }

    suspend fun pauseUnavailable() {
        _reservePaused.value = false
        pauseAll()
    }

    suspend fun resume() {
        refreshAvailability()
        ensureDownloadsScheduled()
    }

    suspend fun ensureDownloadsScheduled() {
        if (_downloadsUnavailable.value != null) return
        if (repository.hasIncompleteDownloads()) {
            scheduler.ensureScheduled()
        }
    }

    suspend fun refreshAvailability() {
        val offline = !connectivity.isOnline.value
        val mobileBlocked = !settings.allowMobileDataDownloads.first() &&
            connectivity.isMetered.value
        _mobileDataPaused.value = mobileBlocked

        val storageUnavailable = !cache.isDownloadStorageHealthy()
        val reserve = settings.downloadReserveBytes.first()
        val freeBytes = storage.selectedAvailableBytes()
        val reserveBlocked = !storageUnavailable &&
            !DownloadSpace.canStartDownload(freeBytes, reserve)
        _reservePaused.value = reserveBlocked

        val signature = "$offline|$mobileBlocked|$storageUnavailable|$reserveBlocked"
        if (signature != lastAvailabilitySignature) {
            lastAvailabilitySignature = signature
            AppLog.info(
                category = LogCategory.DOWNLOAD,
                event = "download.availability",
                fields = mapOf(
                    "online" to !offline,
                    "metered" to connectivity.isMetered.value,
                    "mobileBlocked" to mobileBlocked,
                    "storageUnavailable" to storageUnavailable,
                    "reserveBlocked" to reserveBlocked,
                    "freeBytes" to freeBytes,
                    "reserveBytes" to reserve,
                    "action" to if (offline || mobileBlocked || storageUnavailable || reserveBlocked) {
                        "paused"
                    } else {
                        "resumed"
                    },
                ),
            )
        }

        if (offline || mobileBlocked || storageUnavailable || reserveBlocked) {
            pauseAll()
        } else {
            withContext(Dispatchers.Main) {
                manager?.resumeDownloads()
            }
        }
    }

    suspend fun isIdle(): Boolean = withContext(Dispatchers.Main) {
        manager?.isIdle ?: true
    }

    suspend fun activeRequestIds(): Set<String> = withContext(Dispatchers.Main) {
        manager?.currentDownloads.orEmpty().mapTo(mutableSetOf()) { it.request.id }
    }

    suspend fun syncProgress() {
        awaitReady()
        val active = withContext(Dispatchers.Main) { manager?.currentDownloads.orEmpty() }
        active.forEach { download ->
            repository.updateProgress(
                trackKey = download.request.id,
                bytesDownloaded = download.bytesDownloaded,
                bytesTotal = if (download.contentLength > 0) download.contentLength else 0,
                state = download.state.toDownloadState(),
                error = null,
            )
        }
    }

    private suspend fun reconcile(
        manager: DownloadManager,
        rows: List<net.pipebomb.android.data.download.DownloadEntity>,
    ) {
        val indexed = mutableMapOf<String, Download>()
        withContext(Dispatchers.Main) {
            manager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    indexed[cursor.download.request.id] = cursor.download
                }
            }
        }
        for (download in indexed.values) {
            repository.updateProgress(
                trackKey = download.request.id,
                bytesDownloaded = download.bytesDownloaded,
                bytesTotal = if (download.contentLength > 0) download.contentLength else 0,
                state = download.state.toDownloadState(),
                error = null,
            )
        }
        val plan = planReconcile(rows, indexed.keys)
        plan.missingCompleted.forEach { trackKey ->
            repository.markFailed(
                trackKey,
                "Download file missing. Reconnect storage and retry.",
            )
        }
        plan.failedIncomplete.forEach { trackKey ->
            repository.markFailed(
                trackKey,
                "Download was interrupted. Retry to resume it.",
            )
        }
        withContext(Dispatchers.Main) {
            plan.orphans.forEach(manager::removeDownload)
        }
        if (cache.isDownloadStorageHealthy()) {
            cache.removeUnreferencedDownloadSpans(
                rows.map { it.trackKey } + indexed.keys,
            )
        }
        AppLog.info(
            category = LogCategory.DOWNLOAD,
            event = "download.reconcile",
            fields = mapOf(
                "rows" to rows.size,
                "indexed" to indexed.size,
                "indexHealthy" to plan.indexHealthy,
                "missingMarked" to plan.missingCompleted.size,
                "failedMarked" to plan.failedIncomplete.size,
                "orphans" to plan.orphans.size,
            ),
        )
    }

    private fun Download.stateName(): String = downloadStateName(state)

    private fun downloadStateName(state: Int): String = when (state) {
        Download.STATE_QUEUED -> "queued"
        Download.STATE_STOPPED -> "stopped"
        Download.STATE_DOWNLOADING -> "downloading"
        Download.STATE_COMPLETED -> "completed"
        Download.STATE_FAILED -> "failed"
        Download.STATE_REMOVING -> "removing"
        Download.STATE_RESTARTING -> "restarting"
        else -> "state_$state"
    }

    private fun Int.toDownloadState(): DownloadState = when (this) {
        Download.STATE_COMPLETED -> DownloadState.Completed
        Download.STATE_FAILED -> DownloadState.Failed
        Download.STATE_STOPPED -> DownloadState.Paused
        Download.STATE_DOWNLOADING -> DownloadState.Downloading
        else -> DownloadState.Queued
    }

    private companion object {
        const val START_TIMEOUT_MS = 15_000L
    }
}
