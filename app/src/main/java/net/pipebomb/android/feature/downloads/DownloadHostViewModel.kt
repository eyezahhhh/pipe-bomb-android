package net.pipebomb.android.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.download.DownloadCoordinator
import net.pipebomb.android.playback.download.DownloadScheduler
import net.pipebomb.android.playback.download.DownloadStartResult
import net.pipebomb.android.playback.download.DownloadStarter
import javax.inject.Inject

data class DownloadHostState(
    val downloads: List<DownloadItem> = emptyList(),
    val storageAvailable: Boolean = true,
    val reservePaused: Boolean = false,
    val reserveBytes: Long = AppSettingsStore.DEFAULT_DOWNLOAD_RESERVE_BYTES,
    val availableBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val streamedBytes: Long = 0,
) {
    val byTrack: Map<String, DownloadItem>
        get() = downloads.associateBy { it.entity.trackKey }
}

sealed interface DownloadEffect {
    data class Message(val text: String) : DownloadEffect
}

@HiltViewModel
class DownloadHostViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val starter: DownloadStarter,
    private val coordinator: DownloadCoordinator,
    private val scheduler: DownloadScheduler,
    private val storage: StorageLocationRepository,
    private val settings: AppSettingsStore,
    private val cache: PlaybackCache,
) : ViewModel() {
    private val refresh = MutableStateFlow(0L)
    private val _effects = Channel<DownloadEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val state = combine(
        repository.downloads,
        coordinator.reservePaused,
        settings.downloadReserveBytes,
        storage.revision,
        refresh,
    ) { downloads, reservePaused, reserveBytes, _, _ ->
        DownloadHostState(
            downloads = downloads,
            storageAvailable = storage.resolve().downloadsEnabled && !cache.storageState.isFallback,
            reservePaused = reservePaused,
            reserveBytes = reserveBytes,
            availableBytes = storage.selectedAvailableBytes(),
            downloadedBytes = downloads.sumOf { it.entity.bytesDownloaded },
            streamedBytes = cache.sizeBytes(),
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DownloadHostState(),
        )

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
            val key = item.entity.trackKey
            repository.delete(key)
            cache.removeTrack(key)
            runCatching { coordinator.remove(key) }
            refresh.update { it + 1 }
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
}
