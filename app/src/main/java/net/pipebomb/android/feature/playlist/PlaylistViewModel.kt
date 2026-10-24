package net.pipebomb.android.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

const val PLAYLIST_CHUNK_SIZE = 50

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val totalCount: Int = 0,
    val tracksByIndex: Map<Int, Track> = emptyMap(),
    val loadingChunks: Set<Int> = emptySet(),
    val chunkErrors: Map<Int, String> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private var uuid: String? = null
    private val chunkLock = Any()
    private val chunksInFlight = mutableSetOf<Int>()

    fun load(playlistUuid: String) {
        if (uuid == playlistUuid && _state.value.playlist != null) return
        uuid = playlistUuid
        chunksInFlight.clear()
        viewModelScope.launch {
            _state.value = PlaylistUiState(isLoading = true)
            when (val result = catalogRepository.playlist(playlistUuid)) {
                is ApiResult.Failure -> _state.value = PlaylistUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> {
                    val playlist = result.value
                    val initial = playlist.tracks.orEmpty()
                    val total = playlist.trackCount ?: initial.size
                    val tracksByIndex = initial.mapIndexed { index, entry -> index to entry.track }.toMap()
                    _state.value = PlaylistUiState(
                        isLoading = false,
                        playlist = playlist,
                        totalCount = total,
                        tracksByIndex = tracksByIndex,
                    )
                }
            }
        }
    }

    fun ensureChunk(chunkIndex: Int) {
        val playlist = _state.value.playlist ?: return
        val total = _state.value.totalCount
        val start = chunkIndex * PLAYLIST_CHUNK_SIZE
        if (start >= total) return
        val end = minOf(total, start + PLAYLIST_CHUNK_SIZE)
        val alreadyLoaded = (start until end).all { _state.value.tracksByIndex.containsKey(it) }
        if (alreadyLoaded) return

        synchronized(chunkLock) {
            if (!chunksInFlight.add(chunkIndex)) return
            _state.update {
                it.copy(
                    loadingChunks = it.loadingChunks + chunkIndex,
                    chunkErrors = it.chunkErrors - chunkIndex,
                )
            }
        }

        viewModelScope.launch {
            val amount = end - start
            val result = catalogRepository.playlistTrackPage(
                uuid = playlist.uuid,
                offset = start,
                amount = amount,
            )
            when (result) {
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            loadingChunks = it.loadingChunks - chunkIndex,
                            chunkErrors = it.chunkErrors + (chunkIndex to result.error.userMessage()),
                        )
                    }
                }

                is ApiResult.Success -> {
                    _state.update { current ->
                        val merged = current.tracksByIndex.toMutableMap()
                        result.value.forEachIndexed { localIndex, entry ->
                            merged[start + localIndex] = entry.track
                        }
                        current.copy(
                            tracksByIndex = merged,
                            loadingChunks = current.loadingChunks - chunkIndex,
                            chunkErrors = current.chunkErrors - chunkIndex,
                        )
                    }
                }
            }
            synchronized(chunkLock) { chunksInFlight.remove(chunkIndex) }
        }
    }

    suspend fun loadAllTracks(): List<Track> {
        val playlist = _state.value.playlist ?: return emptyList()
        val total = _state.value.totalCount
        val loaded = _state.value.tracksByIndex
        if (loaded.size >= total) {
            return (0 until total).mapNotNull(loaded::get)
        }
        return when (val all = catalogRepository.allPlaylistTracks(playlist.uuid)) {
            is ApiResult.Success -> all.value.map { it.track }
            is ApiResult.Failure -> (0 until total).mapNotNull(loaded::get)
        }
    }
}
