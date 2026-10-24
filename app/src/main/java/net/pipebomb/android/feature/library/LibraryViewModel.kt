package net.pipebomb.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.paging.SavedAlbumsPagingSource
import net.pipebomb.android.data.paging.SavedArtistsPagingSource
import net.pipebomb.android.data.paging.SavedTracksPagingSource
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
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
}
