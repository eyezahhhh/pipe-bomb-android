package net.pipebomb.android.data

import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.EphemeralSearchDto
import net.pipebomb.android.core.model.EphemeralSearchResults
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.PlaybackHistoryPage
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.PlaylistTrack
import net.pipebomb.android.core.model.PluginLibrary
import net.pipebomb.android.core.model.SavedAlbums
import net.pipebomb.android.core.model.SavedArtists
import net.pipebomb.android.core.model.SavedTracks
import net.pipebomb.android.core.model.SearchDto
import net.pipebomb.android.core.model.SearchResults
import net.pipebomb.android.core.model.SearchSourceSummary
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackIdDto
import net.pipebomb.android.core.model.TrackIdsDto
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.apiCall
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val trackCache: TrackCache,
) {
    suspend fun savedTracks(page: Int, pageSize: Int): ApiResult<SavedTracks> =
        apiCall {
            apiClientFactory.requireApi().getSavedTracks(pageSize = pageSize, page = page)
        }

    suspend fun savedAlbums(page: Int, pageSize: Int): ApiResult<SavedAlbums> =
        apiCall {
            apiClientFactory.requireApi().getSavedAlbums(pageSize = pageSize, page = page)
        }

    suspend fun savedArtists(page: Int, pageSize: Int): ApiResult<SavedArtists> =
        apiCall {
            apiClientFactory.requireApi().getSavedArtists(pageSize = pageSize, page = page)
        }

    suspend fun tracks(keys: List<TrackKey>): ApiResult<List<Track>> {
        val unique = keys.distinct()
        if (unique.isEmpty()) return ApiResult.Success(emptyList())

        val cached = unique.mapNotNull { key -> trackCache.get(key)?.let { key to it } }.toMap()
        val missing = unique.filterNot(cached::containsKey)
        if (missing.isEmpty()) {
            return ApiResult.Success(unique.mapNotNull(cached::get))
        }

        val fetched = mutableListOf<Track>()
        for (chunk in missing.chunked(MAX_BATCH)) {
            when (val result = apiCall {
                apiClientFactory.requireApi().getTracks(
                    TrackIdsDto(
                        chunk.map { TrackIdDto(it.pluginId, it.libraryId, it.trackId) },
                    ),
                )
            }) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> fetched += result.value
            }
        }
        trackCache.putAll(fetched)
        val byKey = cached + fetched.associateBy { it.key }
        return ApiResult.Success(unique.mapNotNull(byKey::get))
    }

    suspend fun track(key: TrackKey): ApiResult<Track> =
        apiCall {
            apiClientFactory.requireApi().getTrack(key.pluginId, key.libraryId, key.trackId)
        }

    suspend fun album(idOrIdentity: String): ApiResult<Album> {
        val identity = parseIdentity(idOrIdentity)
        return apiCall {
            if (identity == null) {
                apiClientFactory.requireApi().getAlbum(idOrIdentity)
            } else {
                apiClientFactory.requireApi().getAlbumByIdentity(
                    identity.pluginId,
                    identity.identifierId,
                    identity.value,
                )
            }
        }
    }

    suspend fun artist(idOrIdentity: String): ApiResult<Artist> {
        val identity = parseIdentity(idOrIdentity)
        return apiCall {
            if (identity == null) {
                apiClientFactory.requireApi().getArtist(idOrIdentity)
            } else {
                apiClientFactory.requireApi().getArtistByIdentity(
                    identity.pluginId,
                    identity.identifierId,
                    identity.value,
                )
            }
        }
    }

    suspend fun ownPlaylists(): ApiResult<List<Playlist>> =
        apiCall { apiClientFactory.requireApi().getOwnPlaylists() }

    suspend fun publicPlaylists(): ApiResult<List<Playlist>> =
        apiCall { apiClientFactory.requireApi().getPublicPlaylists() }

    suspend fun playlist(uuid: String, tracks: Int = 50): ApiResult<Playlist> =
        apiCall { apiClientFactory.requireApi().getPlaylist(uuid, tracks) }

    suspend fun playlistTrackPage(
        uuid: String,
        offset: Int,
        amount: Int,
    ): ApiResult<List<PlaylistTrack>> =
        apiCall {
            apiClientFactory.requireApi().getPlaylistTracks(
                uuid,
                net.pipebomb.android.core.model.PlaylistTracksQuery(offset = offset, amount = amount),
            )
        }

    suspend fun allPlaylistTracks(uuid: String): ApiResult<List<PlaylistTrack>> =
        apiCall { apiClientFactory.requireApi().getAllPlaylistTracks(uuid) }

    suspend fun history(page: Int, pageSize: Int): ApiResult<PlaybackHistoryPage> =
        apiCall {
            apiClientFactory.requireApi().getPlaybackHistory(pageSize = pageSize, page = page)
        }

    suspend fun libraries(): ApiResult<List<PluginLibrary>> =
        apiCall { apiClientFactory.requireApi().getLibraries() }

    suspend fun search(query: String): ApiResult<SearchResults> =
        apiCall {
            apiClientFactory.requireApi().search(
                SearchDto(query = query, attributes = emptyList()),
            )
        }

    suspend fun searchSources(): ApiResult<List<SearchSourceSummary>> =
        apiCall { apiClientFactory.requireApi().getSearchSources() }

    suspend fun ephemeralSources(): ApiResult<List<EphemeralSource>> =
        apiCall { apiClientFactory.requireApi().getEphemeralSources() }

    suspend fun ephemeralSearch(
        pluginId: String,
        sourceId: String,
        query: String,
    ): ApiResult<EphemeralSearchResults> =
        apiCall {
            apiClientFactory.requireApi().searchEphemeral(
                EphemeralSearchDto(pluginId, sourceId, query),
            )
        }

    suspend fun setTrackSaved(key: TrackKey, saved: Boolean): ApiResult<Unit> =
        apiCall {
            val response = if (saved) {
                apiClientFactory.requireApi().saveTrack(key.pluginId, key.libraryId, key.trackId)
            } else {
                apiClientFactory.requireApi().unsaveTrack(key.pluginId, key.libraryId, key.trackId)
            }
            if (!response.isSuccessful) throw HttpException(response)
        }

    suspend fun setAlbumSaved(albumUuid: String, saved: Boolean): ApiResult<Unit> =
        apiCall {
            val response = if (saved) {
                apiClientFactory.requireApi().saveAlbum(albumUuid)
            } else {
                apiClientFactory.requireApi().unsaveAlbum(albumUuid)
            }
            if (!response.isSuccessful) throw HttpException(response)
        }

    suspend fun setArtistSaved(artistUuid: String, saved: Boolean): ApiResult<Unit> =
        apiCall {
            val response = if (saved) {
                apiClientFactory.requireApi().saveArtist(artistUuid)
            } else {
                apiClientFactory.requireApi().unsaveArtist(artistUuid)
            }
            if (!response.isSuccessful) throw HttpException(response)
        }

    private data class IdentityRef(
        val pluginId: String,
        val identifierId: String,
        val value: String,
    )

    private fun parseIdentity(input: String): IdentityRef? {
        val parts = input.split('~')
        if (parts.size != 3 || parts.any { it.isBlank() }) return null
        return IdentityRef(parts[0], parts[1], parts[2])
    }

    private companion object {
        const val MAX_BATCH = 50
    }
}
