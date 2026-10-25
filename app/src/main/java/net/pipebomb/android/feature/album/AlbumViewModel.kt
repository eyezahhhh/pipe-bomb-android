package net.pipebomb.android.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.TrackIdDto
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
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
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    private var albumId: String? = null

    fun load(id: String) {
        if (albumId == id && _state.value.album != null) return
        albumId = id
        viewModelScope.launch {
            _state.value = AlbumUiState(isLoading = true)
            when (val result = catalogRepository.album(id)) {
                is ApiResult.Failure -> _state.value = AlbumUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> _state.value = AlbumUiState(
                    isLoading = false,
                    album = result.value,
                )
            }
        }
    }

    fun toggleSave() {
        val album = _state.value.album ?: return
        val uuid = album.uuid ?: return
        val saved = album.bookmarked == true
        viewModelScope.launch {
            when (catalogRepository.setAlbumSaved(uuid, !saved)) {
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
        val tracks = _state.value.album?.tracks.orEmpty()
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isAddingToPlaylist = playlistUuid, addToPlaylistError = null) }
            when (
                val result = catalogRepository.addTracksToPlaylist(
                    playlistUuid = playlistUuid,
                    trackKeys = tracks.map {
                        TrackIdDto(it.pluginId, it.libraryId, it.trackId)
                    },
                )
            ) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        isAddingToPlaylist = null,
                        addToPlaylistError = result.error.userMessage(),
                    )
                }

                is ApiResult.Success -> _state.update {
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