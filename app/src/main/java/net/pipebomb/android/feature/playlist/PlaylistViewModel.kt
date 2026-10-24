package net.pipebomb.android.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadMoreError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private var uuid: String? = null
    private val loadMutex = Mutex()

    fun load(playlistUuid: String) {
        if (uuid == playlistUuid && _state.value.playlist != null) return
        uuid = playlistUuid
        viewModelScope.launch {
            _state.value = PlaylistUiState(isLoading = true)
            when (val result = catalogRepository.playlist(playlistUuid)) {
                is ApiResult.Failure -> _state.value = PlaylistUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> {
                    val playlist = result.value
                    val loaded = playlist.tracks?.size ?: 0
                    val total = playlist.trackCount ?: loaded
                    _state.value = PlaylistUiState(
                        isLoading = false,
                        playlist = playlist,
                        canLoadMore = loaded < total,
                    )
                }
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch { loadNextChunk() }
    }

    suspend fun loadAllTracks(): List<Track> {
        val playlist = _state.value.playlist ?: return emptyList()
        val loaded = playlist.tracks.orEmpty()
        if (loaded.size >= (playlist.trackCount ?: loaded.size)) {
            return loaded.map { it.track }
        }
        return when (val all = catalogRepository.allPlaylistTracks(playlist.uuid)) {
            is ApiResult.Success -> {
                _state.update {
                    it.copy(
                        playlist = playlist.copy(tracks = all.value, trackCount = all.value.size),
                        canLoadMore = false,
                        isLoadingMore = false,
                    )
                }
                all.value.map { it.track }
            }

            is ApiResult.Failure -> loaded.map { it.track }
        }
    }

    private suspend fun loadNextChunk(): Boolean = loadMutex.withLock {
        val playlist = _state.value.playlist ?: return@withLock false
        val loaded = playlist.tracks.orEmpty()
        val total = playlist.trackCount ?: loaded.size
        if (loaded.size >= total) {
            _state.update { it.copy(canLoadMore = false) }
            return@withLock false
        }
        _state.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        val result = catalogRepository.playlistTrackPage(
            uuid = playlist.uuid,
            offset = loaded.size,
            amount = CHUNK_SIZE,
        )
        when (result) {
            is ApiResult.Failure -> {
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreError = result.error.userMessage(),
                    )
                }
                false
            }

            is ApiResult.Success -> {
                val merged = loaded + result.value
                val expected = playlist.trackCount ?: merged.size
                _state.update {
                    it.copy(
                        playlist = playlist.copy(tracks = merged),
                        isLoadingMore = false,
                        canLoadMore = result.value.isNotEmpty() && merged.size < expected,
                    )
                }
                result.value.isNotEmpty()
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 50
    }
}
