package net.pipebomb.android.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.toThrowable
import net.pipebomb.android.data.CatalogRepository

private const val PAGE_SIZE = 30

class SavedTracksPagingSource(
    private val repository: CatalogRepository,
) : PagingSource<Int, Track>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Track> {
        val page = params.key ?: 1
        return when (val result = repository.savedTracks(page, PAGE_SIZE)) {
            is ApiResult.Failure -> LoadResult.Error(result.error.toThrowable())
            is ApiResult.Success -> LoadResult.Page(
                data = result.value.tracks,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (page * PAGE_SIZE < result.value.total) page + 1 else null,
            )
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Track>): Int? =
        state.anchorPosition?.let { state.closestPageToPosition(it)?.prevKey?.plus(1) }
}

class SavedAlbumsPagingSource(
    private val repository: CatalogRepository,
) : PagingSource<Int, Album>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Album> {
        val page = params.key ?: 1
        return when (val result = repository.savedAlbums(page, PAGE_SIZE)) {
            is ApiResult.Failure -> LoadResult.Error(result.error.toThrowable())
            is ApiResult.Success -> LoadResult.Page(
                data = result.value.albums,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (page * PAGE_SIZE < result.value.total) page + 1 else null,
            )
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Album>): Int? =
        state.anchorPosition?.let { state.closestPageToPosition(it)?.prevKey?.plus(1) }
}

class SavedArtistsPagingSource(
    private val repository: CatalogRepository,
) : PagingSource<Int, Artist>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artist> {
        val page = params.key ?: 1
        return when (val result = repository.savedArtists(page, PAGE_SIZE)) {
            is ApiResult.Failure -> LoadResult.Error(result.error.toThrowable())
            is ApiResult.Success -> LoadResult.Page(
                data = result.value.artists,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (page * PAGE_SIZE < result.value.total) page + 1 else null,
            )
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Artist>): Int? =
        state.anchorPosition?.let { state.closestPageToPosition(it)?.prevKey?.plus(1) }
}
