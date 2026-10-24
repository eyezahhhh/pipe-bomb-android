package net.pipebomb.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.paging.SavedAlbumsPagingSource
import net.pipebomb.android.data.paging.SavedArtistsPagingSource
import net.pipebomb.android.data.paging.SavedTracksPagingSource
import javax.inject.Inject

const val HISTORY_CHUNK_SIZE = 50

data class HistoryUiState(
    val isLoading: Boolean = true,
    val total: Int = 0,
    val tracksByIndex: Map<Int, Track> = emptyMap(),
    val loadedChunks: Set<Int> = emptySet(),
    val loadingChunks: Set<Int> = emptySet(),
    val chunkErrors: Map<Int, String> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    val tracks: Flow<PagingData<Track>> = Pager(
        config = PagingConfig(pageSize = 30, prefetchDistance = 10),
        pagingSourceFactory = { SavedTracksPagingSource(catalogRepository) },
    ).flow.cachedIn(viewModelScope)

    val albums: Flow<PagingData<Album>> = Pager(
        config = PagingConfig(pageSize = 30, prefetchDistance = 10),
        pagingSourceFactory = { SavedAlbumsPagingSource(catalogRepository) },
    ).flow.cachedIn(viewModelScope)

    val artists: Flow<PagingData<Artist>> = Pager(
        config = PagingConfig(pageSize = 30, prefetchDistance = 10),
        pagingSourceFactory = { SavedArtistsPagingSource(catalogRepository) },
    ).flow.cachedIn(viewModelScope)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()

    private val historyChunksInFlight = mutableSetOf<Int>()
    private val historyChunkLock = Any()
    private var historyStarted = false

    init {
        viewModelScope.launch {
            val own = catalogRepository.ownPlaylists()
            val public = catalogRepository.publicPlaylists()
            val all = (own as? ApiResult.Success)?.value.orEmpty() +
                (public as? ApiResult.Success)?.value.orEmpty()
            _playlists.value = all.distinctBy { it.uuid }
        }
    }

    fun ensureHistoryLoaded() {
        if (historyStarted) return
        historyStarted = true
        loadInitialHistory()
    }

    private fun loadInitialHistory() {
        viewModelScope.launch {
            _history.value = HistoryUiState(isLoading = true)
            when (val result = catalogRepository.history(page = 1, pageSize = HISTORY_CHUNK_SIZE)) {
                is ApiResult.Failure -> _history.value = HistoryUiState(
                    isLoading = false,
                    error = result.error.userMessage(),
                )

                is ApiResult.Success -> {
                    val page = result.value
                    val entries = page.entries
                    val tracksByIndex = mutableMapOf<Int, Track>()
                    entries.forEachIndexed { index, entry ->
                        entry.track?.let { tracksByIndex[index] = it }
                    }
                    _history.value = HistoryUiState(
                        isLoading = false,
                        total = page.total,
                        tracksByIndex = tracksByIndex,
                        loadedChunks = if (page.total > 0) setOf(0) else emptySet(),
                    )
                }
            }
        }
    }

    fun retryHistory() {
        historyStarted = true
        historyChunksInFlight.clear()
        loadInitialHistory()
    }

    fun ensureHistoryChunk(chunkIndex: Int) {
        val current = _history.value
        if (current.total <= 0) return
        if (chunkIndex in current.loadedChunks) return
        synchronized(historyChunkLock) {
            if (!historyChunksInFlight.add(chunkIndex)) return
            _history.update {
                it.copy(
                    loadingChunks = it.loadingChunks + chunkIndex,
                    chunkErrors = it.chunkErrors - chunkIndex,
                )
            }
        }
        viewModelScope.launch {
            val result = catalogRepository.history(
                page = chunkIndex + 1,
                pageSize = HISTORY_CHUNK_SIZE,
            )
            when (result) {
                is ApiResult.Failure -> {
                    _history.update {
                        it.copy(
                            loadingChunks = it.loadingChunks - chunkIndex,
                            chunkErrors = it.chunkErrors + (chunkIndex to result.error.userMessage()),
                        )
                    }
                }

                is ApiResult.Success -> {
                    _history.update { state ->
                        val merged = state.tracksByIndex.toMutableMap()
                        val start = chunkIndex * HISTORY_CHUNK_SIZE
                        result.value.entries.forEachIndexed { localIndex, entry ->
                            entry.track?.let { merged[start + localIndex] = it }
                        }
                        state.copy(
                            total = result.value.total,
                            tracksByIndex = merged,
                            loadedChunks = state.loadedChunks + chunkIndex,
                            loadingChunks = state.loadingChunks - chunkIndex,
                            chunkErrors = state.chunkErrors - chunkIndex,
                        )
                    }
                }
            }
            synchronized(historyChunkLock) { historyChunksInFlight.remove(chunkIndex) }
        }
    }
}
