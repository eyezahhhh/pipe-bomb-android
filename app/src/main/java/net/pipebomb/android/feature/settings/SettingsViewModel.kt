package net.pipebomb.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pipebomb.android.BuildConfig
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.net.DataStoreServerUrlProvider
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.SessionState
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.storage.AppRestarter
import net.pipebomb.android.data.storage.StorageLocation
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.download.DownloadCoordinator
import net.pipebomb.android.playback.download.DownloadScheduler
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val cacheBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val storageLabel: String = "Internal storage",
    val storageAvailable: Boolean = true,
    val restartRequired: Boolean = false,
    val storageLocations: List<StorageLocation> = emptyList(),
    val selectedLocationId: String = StorageLocationRepository.INTERNAL_ID,
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    val reserveBytes: Long = AppSettingsStore.DEFAULT_DOWNLOAD_RESERVE_BYTES,
    val reservePaused: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverUrlProvider: DataStoreServerUrlProvider,
    private val authRepository: AuthRepository,
    private val playbackCache: PlaybackCache,
    private val playbackController: PlaybackController,
    private val storage: StorageLocationRepository,
    private val settings: AppSettingsStore,
    private val downloads: DownloadRepository,
    private val coordinator: DownloadCoordinator,
    private val scheduler: DownloadScheduler,
    private val restarter: AppRestarter,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            serverUrlProvider.urls.collect { url ->
                _state.update { it.copy(serverUrl = url?.toString().orEmpty()) }
            }
        }
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _state.update {
                    it.copy(
                        username = (session as? SessionState.Authenticated)?.user?.username.orEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.downloadReserveBytes.collect { reserve ->
                _state.update { it.copy(reserveBytes = reserve) }
            }
        }
        viewModelScope.launch {
            storage.revision.collect { refresh() }
        }
        viewModelScope.launch {
            downloads.totalBytes.collect { bytes ->
                _state.update { it.copy(downloadedBytes = bytes) }
            }
        }
        viewModelScope.launch {
            coordinator.reservePaused.collect { paused ->
                _state.update { it.copy(reservePaused = paused) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val storageState = storage.resolve()
            val locations = withContext(Dispatchers.IO) { storage.availableLocations() }
            val reserve = settings.downloadReserveBytes.first()
            val totalBytes = downloads.totalBytes.first()
            _state.update {
                it.copy(
                    serverUrl = serverUrlProvider.current()?.toString().orEmpty(),
                    username = (authRepository.session.value as? SessionState.Authenticated)
                        ?.user?.username.orEmpty(),
                    cacheBytes = playbackCache.sizeBytes(),
                    downloadedBytes = totalBytes,
                    storageLabel = storage.selectedLocationLabel(),
                    storageAvailable = storageState.downloadsEnabled &&
                        !playbackCache.storageState.isFallback,
                    restartRequired = storageState.downloadsEnabled &&
                        playbackCache.storageState.isFallback,
                    storageLocations = locations,
                    selectedLocationId = storageState.selectedId,
                    freeBytes = storage.selectedAvailableBytes(),
                    totalBytes = storage.selectedTotalBytes(),
                    reserveBytes = reserve,
                    appVersion = BuildConfig.VERSION_NAME,
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackCache.clearStreamed()
            refresh()
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloads.all()
            scheduler.cancel()
            runCatching { coordinator.pauseAll() }
            items.forEach { playbackCache.removeTrack(it.trackKey) }
            downloads.deleteAll()
            runCatching { coordinator.removeAll() }
            refresh()
        }
    }

    fun setReserveBytes(bytes: Long) {
        viewModelScope.launch {
            settings.setDownloadReserveBytes(bytes)
            coordinator.resume()
            if (!coordinator.reservePaused.value) scheduler.schedule()
            refresh()
        }
    }

    fun resumeDownloads() {
        viewModelScope.launch {
            coordinator.resume()
            if (!coordinator.reservePaused.value) scheduler.schedule()
            refresh()
        }
    }

    fun switchStorage(location: StorageLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!storage.requestSwitch(location)) return@launch
            scheduler.cancel()
            runCatching { coordinator.pauseAll() }
            downloads.deleteAll()
            withContext(Dispatchers.Main) { playbackController.clearQueue() }
            restarter.restart()
        }
    }

    fun restartApp() {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.cancel()
            runCatching { coordinator.pauseAll() }
            restarter.restart()
        }
    }

    fun logout() {
        viewModelScope.launch {
            playbackController.clearQueue()
            authRepository.logout()
        }
    }

    fun changeServer() {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.cancel()
            runCatching { coordinator.pauseAll() }
            runCatching { coordinator.removeAll() }
            downloads.deleteAll()
            withContext(Dispatchers.Main) { playbackController.clearQueue() }
            playbackCache.clearStreamed()
            authRepository.clearSession()
            serverUrlProvider.clear()
            refresh()
        }
    }
}
