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
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.paging.SavedAlbumsPagingSource
import net.pipebomb.android.data.paging.SavedArtistsPagingSource
import net.pipebomb.android.data.paging.SavedTracksPagingSource
import javax.inject.Inject

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

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            val own = catalogRepository.ownPlaylists()
            val public = catalogRepository.publicPlaylists()
            val all = (own as? ApiResult.Success)?.value.orEmpty() +
                (public as? ApiResult.Success)?.value.orEmpty()
            _playlists.value = all.distinctBy { it.uuid }
        }
        viewModelScope.launch {
            val result = catalogRepository.history(page = 1, pageSize = 100)
            _history.value = (result as? ApiResult.Success)?.value?.entries.orEmpty()
                .mapNotNull { it.track }
                .distinctBy { it.key }
        }
    }
}
