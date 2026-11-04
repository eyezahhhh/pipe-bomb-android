package net.pipebomb.android.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.AlbumEphemeralContent
import net.pipebomb.android.core.model.EphemeralContentState
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.parseIdentityRef
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.download.DownloadCollectionType
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.offlineAlbum
import net.pipebomb.android.playback.download.CollectionSyncScheduler
import javax.inject.Inject

data class AlbumUiState(
    val isLoading: Boolean = true,
    val album: Album? = null,
    val error: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val isAddingToPlaylist: String? = null,
    val addToPlaylistError: String? = null,
    val addToPlaylistSuccess: Boolean = false,
    val isOfflineCopy: Boolean = false,
    val ephemeral: EphemeralContentState<AlbumEphemeralContent> =
        EphemeralContentState(),
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val connectivity: ConnectivityStatus,
    private val collectionSync: CollectionSyncScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    private var albumId: String? = null
    private var ephemeralJob: Job? = null

    fun load(id: String) {
        if (albumId == id && _state.value.album != null) {
            if (!_state.value.ephemeral.visible) retryEphemeral()
            return
        }
        albumId = id
        ephemeralJob?.cancel()
        viewModelScope.launch {
            _state.value = AlbumUiState(isLoading = true)
            if (!connectivity.isOnline.value) {
                loadOffline(id)
                return@launch
            }
            when (val result = catalogRepository.album(id)) {
                is ApiResult.Failure -> loadOffline(id, result.userMessage())
                is ApiResult.Success -> {
                    _state.value = AlbumUiState(
                        isLoading = false,
                        album = result.value,
                    )
                    loadEphemeral(id)
                }
            }
        }
    }

    fun selectEphemeralSource(source: EphemeralSource) {
        val id = albumId ?: return
        if (_state.value.ephemeral.isIdentity) return
        if (_state.value.ephemeral.selectedSource == source) return
        ephemeralJob?.cancel()
        _state.update {
            it.copy(
                ephemeral = it.ephemeral.copy(
                    selectedSource = source,
                    isLoadingContent = true,
                    content = null,
                ),
            )
        }
        ephemeralJob = viewModelScope.launch {
            fetchEphemeralContent(id, source)
        }
    }

    fun retryEphemeral() {
        val id = albumId ?: return
        if (_state.value.album == null || _state.value.ephemeral.visible) return
        loadEphemeral(id)
    }

    fun toggleSave() {
        val album = _state.value.album ?: return
        val id = album.uuid ?: album.identityKey ?: return
        val saved = album.bookmarked == true
        if (album.uuid == null && saved) return
        viewModelScope.launch {
            when (catalogRepository.setAlbumSaved(id, !saved)) {
                is ApiResult.Success -> _state.update {
                    it.copy(album = it.album?.copy(bookmarked = !saved))
                }

                is ApiResult.Failure -> Unit
            }
        }
    }

    fun loadPlaylists() {
        _state.update {
            it.copy(
                isLoadingPlaylists = true,
                addToPlaylistSuccess = false,
                addToPlaylistError = null,
            )
        }
        viewModelScope.launch {
            when (val result = catalogRepository.ownPlaylists()) {
                is ApiResult.Success -> _state.update {
                    it.copy(playlists = result.value, isLoadingPlaylists = false)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        isLoadingPlaylists = false,
                        addToPlaylistError = result.error.userMessage(),
                    )
                }
            }
        }
    }

    fun addToPlaylist(playlistUuid: String) {
        val album = _state.value.album ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAddingToPlaylist = playlistUuid, addToPlaylistError = null) }
            when (val result = catalogRepository.addAlbumToPlaylist(playlistUuid, album)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        isAddingToPlaylist = null,
                        addToPlaylistError = result.error.userMessage(),
                    )
                }

                is ApiResult.Success -> {
                    collectionSync.syncNow(
                        DownloadCollectionType.Playlist.collectionId(playlistUuid),
                    )
                    _state.update {
                        it.copy(
                            isAddingToPlaylist = null,
                            addToPlaylistError = null,
                            addToPlaylistSuccess = true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadOffline(id: String, fallbackError: String? = null) {
        val album = downloadRepository.offlineAlbum(id)
        _state.value = if (album != null) {
            AlbumUiState(
                isLoading = false,
                album = album,
                isOfflineCopy = true,
            )
        } else {
            AlbumUiState(
                isLoading = false,
                error = fallbackError ?: "This album isn't downloaded. Connect to view it.",
            )
        }
    }

    private fun loadEphemeral(id: String) {
        ephemeralJob?.cancel()
        if (!connectivity.isOnline.value) {
            _state.update { it.copy(ephemeral = EphemeralContentState()) }
            return
        }
        val identity = parseIdentityRef(id)
        if (identity != null) {
            _state.update {
                it.copy(
                    ephemeral = EphemeralContentState(
                        isIdentity = true,
                        isLoadingContent = true,
                    ),
                )
            }
            ephemeralJob = viewModelScope.launch {
                when (
                    val result = catalogRepository.albumEphemeralContentByIdentity(
                        identity.pluginId,
                        identity.identifierId,
                        identity.value,
                    )
                ) {
                    is ApiResult.Success -> _state.update {
                        it.copy(
                            ephemeral = EphemeralContentState(
                                isIdentity = true,
                                content = result.value,
                                selectedSource = result.value.source,
                            ),
                        )
                    }

                    is ApiResult.Failure -> _state.update {
                        it.copy(ephemeral = EphemeralContentState(isIdentity = true))
                    }
                }
            }
            return
        }

        _state.update {
            it.copy(ephemeral = EphemeralContentState(isLoadingSources = true))
        }
        ephemeralJob = viewModelScope.launch {
            when (val result = catalogRepository.albumEphemeralSources(id)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(ephemeral = EphemeralContentState())
                }

                is ApiResult.Success -> {
                    if (result.value.isEmpty()) {
                        _state.update {
                            it.copy(ephemeral = EphemeralContentState())
                        }
                    } else {
                        val previous = _state.value.ephemeral.selectedSource
                        val selected = result.value.firstOrNull {
                            it.pluginId == previous?.pluginId && it.id == previous.id
                        } ?: result.value.first()
                        _state.update {
                            it.copy(
                                ephemeral = it.ephemeral.copy(
                                    isLoadingSources = false,
                                    sources = result.value,
                                    selectedSource = selected,
                                    isLoadingContent = true,
                                    content = null,
                                ),
                            )
                        }
                        fetchEphemeralContent(id, selected)
                    }
                }
            }
        }
    }

    private suspend fun fetchEphemeralContent(id: String, source: EphemeralSource) {
        when (val result = catalogRepository.albumEphemeralContent(id, source)) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    ephemeral = it.ephemeral.copy(
                        isLoadingContent = false,
                        content = result.value,
                        selectedSource = result.value.source,
                    ),
                )
            }

            is ApiResult.Failure -> _state.update {
                it.copy(ephemeral = EphemeralContentState())
            }
        }
    }
}
