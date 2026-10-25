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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.di.ApplicationScope
import net.pipebomb.android.playback.PlaybackCache
import javax.inject.Inject
import javax.inject.Singleton

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
    private val scheduler: DownloadScheduler,
) {
    private val initialized = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<Unit>()
    private val _reservePaused = MutableStateFlow(false)
    val reservePaused: StateFlow<Boolean> = _reservePaused.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var manager: DownloadManager? = null

    fun start() {
        if (started) return
        started = true
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
                storage.revision.collect {
                    if (!storage.resolve().downloadsEnabled) pauseUnavailable()
                }
            }
            val reserve = settings.downloadReserveBytes.first()
            when {
                !storage.resolve().downloadsEnabled || cache.storageState.isFallback -> {
                    _reservePaused.value = false
                }

                !DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve) -> {
                    _reservePaused.value = true
                }

                else -> withContext(Dispatchers.Main) { downloadManager.resumeDownloads() }
            }
            if (storage.resolve().downloadsEnabled &&
                !cache.storageState.isFallback &&
                rows.any { it.state == DownloadState.Queued.name }
            ) {
                scheduler.schedule()
            }
        }
    }

    suspend fun awaitReady() {
        start()
        ready.await()
    }

    suspend fun add(download: androidx.media3.exoplayer.offline.DownloadRequest) {
        awaitReady()
        withContext(Dispatchers.Main) {
            manager?.addDownload(download)
        }
    }

    suspend fun remove(trackKey: String) {
        awaitReady()
        withContext(Dispatchers.Main) {
            manager?.removeDownload(trackKey)
        }
    }

    suspend fun removeAll() {
        awaitReady()
        withContext(Dispatchers.Main) {
            manager?.removeAllDownloads()
        }
    }

    suspend fun pauseForReserve() {
        _reservePaused.value = true
        withContext(Dispatchers.Main) {
            manager?.pauseDownloads()
        }
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
        val reserve = settings.downloadReserveBytes.first()
        if (!storage.resolve().downloadsEnabled ||
            cache.storageState.isFallback ||
            !DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve)
        ) {
            _reservePaused.value = true
            return
        }
        _reservePaused.value = false
        withContext(Dispatchers.Main) {
            manager?.resumeDownloads()
        }
    }

    suspend fun refreshReserveState() {
        val reserve = settings.downloadReserveBytes.first()
        if (!storage.resolve().downloadsEnabled ||
            cache.storageState.isFallback ||
            !DownloadSpace.canStartDownload(storage.selectedAvailableBytes(), reserve)
        ) {
            pauseForReserve()
        }
    }

    suspend fun isIdle(): Boolean = withContext(Dispatchers.Main) {
        manager?.isIdle ?: true
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
            repository.markFailed(
                row.trackKey,
                "Download was interrupted. Retry to resume it.",
            )
            if (row.state == DownloadState.Completed.name) {
                repository.delete(row.trackKey)
                cache.removeTrack(row.trackKey)
            }
        }
        val rowIds = rows.mapTo(mutableSetOf()) { it.trackKey }
        val orphans = indexed.keys.filterNot(rowIds::contains)
        withContext(Dispatchers.Main) {
            orphans.forEach(manager::removeDownload)
        }
    }

    private fun Int.toDownloadState(): DownloadState = when (this) {
        Download.STATE_COMPLETED -> DownloadState.Completed
        Download.STATE_FAILED -> DownloadState.Failed
        Download.STATE_STOPPED -> DownloadState.Paused
        Download.STATE_DOWNLOADING -> DownloadState.Downloading
        else -> DownloadState.Queued
    }
}
