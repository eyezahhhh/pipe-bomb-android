package net.pipebomb.android.playback.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val pins: DownloadPinRegistry,
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
            fields = mapOf("maxUnpinnedBytes" to PlaybackCache.MAX_UNPINNED_BYTES),
        )
        val downloadManager = DownloadManager(
            context,
            databaseProvider,
            cache.cache,
            DefaultHttpDataSource.Factory(),
            Runnable::run,
        )
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
            initialized.await()
            val rows = repository.all()
            pins.setPinned(rows.map { it.key })
            reconcile(downloadManager, rows)
            cache.enablePinnedEviction()
            ready.complete(Unit)
            scope.launch {
                repository.downloads.collect { downloads ->
                    pins.setPinned(downloads.map { it.key })
                    cache.refreshPins()
                }
            }
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
        if (repository.hasIncompleteDownloads()) {
            scheduler.ensureScheduled()
        }
    }

    suspend fun refreshAvailability() {
        val offline = !connectivity.isOnline.value
        val mobileBlocked = !settings.allowMobileDataDownloads.first() &&
            connectivity.isMetered.value
        _mobileDataPaused.value = mobileBlocked

        val storageUnavailable =
            !storage.resolve().downloadsEnabled || cache.storageState.isFallback
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
        for (row in rows) {
            if (indexed.containsKey(row.trackKey)) continue
            if (row.state == DownloadState.Pending.name ||
                row.state == DownloadState.Unsupported.name
            ) {
                continue
            }
            if (row.state == DownloadState.Completed.name) {
                repository.delete(row.trackKey)
                cache.removeTrack(row.trackKey)
                continue
            }
            repository.markFailed(
                row.trackKey,
                "Download was interrupted. Retry to resume it.",
            )
        }
        val rowIds = rows.mapTo(mutableSetOf()) { it.trackKey }
        val orphans = indexed.keys.filterNot(rowIds::contains)
        withContext(Dispatchers.Main) {
            orphans.forEach(manager::removeDownload)
        }
        AppLog.info(
            category = LogCategory.DOWNLOAD,
            event = "download.reconcile",
            fields = mapOf(
                "rows" to rows.size,
                "indexed" to indexed.size,
                "orphans" to orphans.size,
                "completedDeleted" to rows.count { row ->
                    row.state == DownloadState.Completed.name &&
                        !indexed.containsKey(row.trackKey)
                },
                "failedMarked" to rows.count { row ->
                    !indexed.containsKey(row.trackKey) &&
                        row.state != DownloadState.Completed.name &&
                        row.state != DownloadState.Pending.name &&
                        row.state != DownloadState.Unsupported.name
                },
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
}
