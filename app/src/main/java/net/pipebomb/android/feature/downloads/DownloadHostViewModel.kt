package net.pipebomb.android.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.SessionState
import net.pipebomb.android.data.download.DownloadCollectionDraft
import net.pipebomb.android.data.download.DownloadCollectionItem
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.data.download.DownloadRemoveResult
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.download.CollectionStartResult
import net.pipebomb.android.playback.download.CollectionSyncScheduler
import net.pipebomb.android.playback.download.DownloadCoordinator
import net.pipebomb.android.playback.download.DownloadScheduler
import net.pipebomb.android.playback.download.DownloadStartResult
import net.pipebomb.android.playback.download.DownloadStarter
import javax.inject.Inject

data class DownloadHostState(
    val downloads: List<DownloadItem> = emptyList(),
    val collections: List<DownloadCollectionItem> = emptyList(),
    val storageAvailable: Boolean = true,
    val reservePaused: Boolean = false,
    val reserveBytes: Long = AppSettingsStore.DEFAULT_DOWNLOAD_RESERVE_BYTES,
    val availableBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val streamedBytes: Long = 0,
    val bulkInProgress: Boolean = false,
    val accountUuid: String? = null,
    val accountName: String? = null,
) {
    val byTrack: Map<String, DownloadItem>
        get() = downloads.associateBy { it.entity.trackKey }
    val byCollection: Map<String, DownloadCollectionItem>
        get() = collections.associateBy { it.id }
}

sealed interface DownloadEffect {
    data class Message(val text: String) : DownloadEffect
    data class BlockedRemoval(val collectionTitles: List<String>) : DownloadEffect
}

@HiltViewModel
class DownloadHostViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val starter: DownloadStarter,
    private val coordinator: DownloadCoordinator,
    private val scheduler: DownloadScheduler,
    private val collectionSync: CollectionSyncScheduler,
    private val storage: StorageLocationRepository,
    private val settings: AppSettingsStore,
    private val cache: PlaybackCache,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0L)
    private val bulkInProgress = MutableStateFlow(false)
    private val _effects = Channel<DownloadEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val libraryState = combine(repository.downloads, repository.collections) { downloads, collections ->
        downloads to collections
    }
    private val runtimeState = combine(
        coordinator.reservePaused,
        settings.downloadReserveBytes,
        storage.revision,
    ) { reservePaused, reserveBytes, revision ->
        Triple(reservePaused, reserveBytes, revision)
    }

    val state = combine(
        libraryState,
        runtimeState,
        refresh,
        bulkInProgress,
        authRepository.session,
    ) { library, runtime, _, bulk, sessionValue ->
        val session = sessionValue as? SessionState.Authenticated
        DownloadHostState(
            downloads = library.first,
            collections = library.second,
            storageAvailable = storage.resolve().downloadsEnabled && !cache.storageState.isFallback,
            reservePaused = runtime.first,
            reserveBytes = runtime.second,
            availableBytes = storage.selectedAvailableBytes(),
            downloadedBytes = library.first.sumOf { it.entity.bytesDownloaded },
            streamedBytes = cache.sizeBytes(),
            bulkInProgress = bulk,
            accountUuid = session?.user?.uuid,
            accountName = session?.user?.username,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DownloadHostState(),
        )

    init {
        collectionSync.syncNow()
    }

    fun start(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = starter.start(track)) {
                DownloadStartResult.Started -> send("Download queued")
                DownloadStartResult.Unsupported -> send("HLS downloads are not supported yet")
                DownloadStartResult.AlreadyPresent -> send("Already downloaded or queued")
                is DownloadStartResult.Error -> send(result.message)
            }
            refresh.update { it + 1 }
        }
    }

    fun subscribeCollection(draft: DownloadCollectionDraft, tracks: List<Track>) {
        if (bulkInProgress.value) return
        viewModelScope.launch(Dispatchers.IO) {
            bulkInProgress.value = true
            try {
                when (val result = starter.subscribeCollection(draft, tracks)) {
                    is CollectionStartResult.Started -> {
                        collectionSync.syncNow(draft.type.collectionId(draft.sourceId))
                        val suffix = if (result.skipped > 0) {
                            " · ${result.skipped} already present"
                        } else {
                            ""
                        }
                        send("Keeping ${draft.title} downloaded$suffix")
                    }

                    is CollectionStartResult.Error -> send(result.message)
                }
            } finally {
                bulkInProgress.value = false
                refresh.update { it + 1 }
            }
        }
    }

    fun stopKeeping(collection: DownloadCollectionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val released = repository.unsubscribeCollection(collection.id)
            released.forEach { key ->
                cache.removeTrack(key)
                coordinator.remove(key)
            }
            send("Stopped keeping ${collection.title}")
            refresh.update { it + 1 }
        }
    }

    fun syncCollection(collectionId: String) {
        collectionSync.syncNow(collectionId)
    }

    fun syncCollections() {
        collectionSync.syncNow()
    }

    fun retry(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = starter.retry(item.key)) {
                DownloadStartResult.Started -> send("Download queued")
                DownloadStartResult.Unsupported -> send("HLS downloads are not supported yet")
                DownloadStartResult.AlreadyPresent -> send("Already downloaded or queued")
                is DownloadStartResult.Error -> send(result.message)
            }
            refresh.update { it + 1 }
        }
    }

    fun remove(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.remove(item)) {
                is DownloadRemoveResult.Blocked -> sendBlocked(result.collectionTitles)
                DownloadRemoveResult.Removed -> {
                    cache.removeTrack(item.entity.trackKey)
                    runCatching { coordinator.remove(item.entity.trackKey) }
                    refresh.update { it + 1 }
                }
            }
        }
    }

    fun removeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = repository.all()
            scheduler.cancel()
            runCatching { coordinator.pauseAll() }
            items.forEach { cache.removeTrack(it.trackKey) }
            repository.deleteAll()
            runCatching { coordinator.removeAll() }
            refresh.update { it + 1 }
        }
    }

    fun clearStreamedCache() {
        viewModelScope.launch(Dispatchers.IO) {
            cache.clearStreamed()
            refresh.update { it + 1 }
        }
    }

    fun resume() {
        viewModelScope.launch(Dispatchers.IO) {
            coordinator.resume()
            if (!coordinator.reservePaused.value) scheduler.schedule()
            refresh.update { it + 1 }
        }
    }

    fun refresh() {
        refresh.update { it + 1 }
    }

    private fun send(message: String) {
        viewModelScope.launch { _effects.send(DownloadEffect.Message(message)) }
    }

    private fun sendBlocked(titles: List<String>) {
        viewModelScope.launch { _effects.send(DownloadEffect.BlockedRemoval(titles)) }
    }
}
