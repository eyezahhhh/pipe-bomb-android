package net.pipebomb.android.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pipebomb.android.core.model.AddPlaylistTracksDto
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.AlbumEphemeralContent
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.ArtistEphemeralContent
import net.pipebomb.android.core.model.EphemeralSearchDto
import net.pipebomb.android.core.model.EphemeralSearchResults
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.EphemeralSourceDto
import net.pipebomb.android.core.model.EphemeralTrack
import net.pipebomb.android.core.model.NewPlaylistAlbumDto
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
import net.pipebomb.android.core.model.UpdatePlaylistTracksDto
import net.pipebomb.android.core.model.parseIdentityRef
import net.pipebomb.android.core.model.toTrack
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
    private val fetchMutex = Mutex()

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

        return fetchMutex.withLock {
            val freshCached = missing.mapNotNull { key -> trackCache.get(key)?.let { key to it } }.toMap()
            val stillMissing = missing.filterNot(freshCached::containsKey)
            if (stillMissing.isEmpty()) {
                return@withLock ApiResult.Success(unique.mapNotNull((cached + freshCached)::get))
            }

            val fetched = mutableListOf<Track>()
            for (chunk in stillMissing.chunked(MAX_BATCH)) {
                when (val result = apiCall {
                    apiClientFactory.requireApi().getTracks(
                        TrackIdsDto(
                            chunk.map { TrackIdDto(it.pluginId, it.libraryId, it.trackId) },
                        ),
                    )
                }) {
                    is ApiResult.Failure -> return@withLock result
                    is ApiResult.Success -> fetched += result.value
                }
            }
            trackCache.putAll(fetched)
            val byKey = cached + freshCached + fetched.associateBy { it.key }
            ApiResult.Success(unique.mapNotNull(byKey::get))
        }
    }

    suspend fun track(key: TrackKey): ApiResult<Track> =
        apiCall {
            apiClientFactory.requireApi().getTrack(key.pluginId, key.libraryId, key.trackId)
        }

    suspend fun album(idOrIdentity: String): ApiResult<Album> {
        val identity = parseIdentityRef(idOrIdentity)
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
        val identity = parseIdentityRef(idOrIdentity)
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

    suspend fun artistEphemeralSources(
        artistUuid: String,
    ): ApiResult<List<EphemeralSource>> =
        apiCall("artist.ephemeral.sources") {
            apiClientFactory.requireApi().getArtistEphemeralSources(artistUuid)
        }

    suspend fun artistEphemeralContent(
        artistUuid: String,
        source: EphemeralSource,
    ): ApiResult<ArtistEphemeralContent> =
        apiCall("artist.ephemeral.content") {
            apiClientFactory.requireApi().getArtistEphemeralContent(
                artistUuid,
                EphemeralSourceDto(source.pluginId, source.id),
            )
        }.cacheEphemeralTracks { it.tracks }

    suspend fun artistEphemeralContentByIdentity(
        pluginId: String,
        identifierId: String,
        identity: String,
    ): ApiResult<ArtistEphemeralContent> =
        apiCall("artist.ephemeral.identity") {
            apiClientFactory.requireApi().getArtistEphemeralContentByIdentity(
                pluginId,
                identifierId,
                identity,
            )
        }.cacheEphemeralTracks { it.tracks }

    suspend fun albumEphemeralSources(
        albumUuid: String,
    ): ApiResult<List<EphemeralSource>> =
        apiCall("album.ephemeral.sources") {
            apiClientFactory.requireApi().getAlbumEphemeralSources(albumUuid)
        }

    suspend fun albumEphemeralContent(
        albumUuid: String,
        source: EphemeralSource,
    ): ApiResult<AlbumEphemeralContent> =
        apiCall("album.ephemeral.content") {
            apiClientFactory.requireApi().getAlbumEphemeralContent(
                albumUuid,
                EphemeralSourceDto(source.pluginId, source.id),
            )
        }.cacheEphemeralTracks { it.tracks }

    suspend fun albumEphemeralContentByIdentity(
        pluginId: String,
        identifierId: String,
        identity: String,
    ): ApiResult<AlbumEphemeralContent> =
        apiCall("album.ephemeral.identity") {
            apiClientFactory.requireApi().getAlbumEphemeralContentByIdentity(
                pluginId,
                identifierId,
                identity,
            )
        }.cacheEphemeralTracks { it.tracks }

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

    suspend fun deletePlaylist(uuid: String): ApiResult<Unit> =
        apiCall {
            val response = apiClientFactory.requireApi().deletePlaylist(uuid)
            if (!response.isSuccessful) throw HttpException(response)
        }

    suspend fun playlistMembers(uuid: String): ApiResult<List<net.pipebomb.android.core.model.PlaylistMember>> =
        apiCall { apiClientFactory.requireApi().getPlaylistMembers(uuid) }

    suspend fun setPlaylistMember(
        uuid: String,
        userUuid: String,
        role: String,
    ): ApiResult<net.pipebomb.android.core.model.PlaylistMember> =
        apiCall {
            apiClientFactory.requireApi().upsertPlaylistMember(
                uuid,
                userUuid,
                net.pipebomb.android.core.model.UpsertPlaylistMemberDto(role),
            )
        }

    suspend fun removePlaylistMember(uuid: String, userUuid: String): ApiResult<Unit> =
        apiCall {
            val response = apiClientFactory.requireApi().removePlaylistMember(uuid, userUuid)
            if (!response.isSuccessful) throw HttpException(response)
        }

    suspend fun searchUsers(query: String): ApiResult<List<net.pipebomb.android.core.model.User>> =
        apiCall { apiClientFactory.requireApi().searchUsers(query) }

    suspend fun addTracksToPlaylist(
        playlistUuid: String,
        trackKeys: List<TrackIdDto>,
    ): ApiResult<Playlist> =
        apiCall {
            apiClientFactory.requireApi().updatePlaylistTracks(
                playlistUuid,
                UpdatePlaylistTracksDto(
                    add = AddPlaylistTracksDto(
                        tracks = trackKeys,
                        albums = emptyList(),
                    ),
                    remove = emptyList(),
                ),
            )
        }

    suspend fun addAlbumToPlaylist(
        playlistUuid: String,
        album: Album,
    ): ApiResult<Playlist> {
        val identity = album.identities?.firstOrNull()
        if (album.uuid == null &&
            (identity == null || identity.pluginId.isBlank() ||
                identity.identityId.isBlank() || identity.value.isBlank())
        ) {
            return ApiResult.Failure(
                net.pipebomb.android.core.net.ApiError.Local(
                    "This album cannot be added to a playlist.",
                ),
            )
        }
        val albumDto = if (album.uuid != null) {
            NewPlaylistAlbumDto(uuid = album.uuid)
        } else {
            NewPlaylistAlbumDto(
                pluginId = identity?.pluginId,
                identityId = identity?.identityId,
                identity = identity?.value,
            )
        }
        return apiCall("playlist.addAlbum") {
            apiClientFactory.requireApi().updatePlaylistTracks(
                playlistUuid,
                UpdatePlaylistTracksDto(
                    add = AddPlaylistTracksDto(
                        tracks = null,
                        albums = listOf(albumDto),
                    ),
                    remove = null,
                ),
            )
        }
    }

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
    ): ApiResult<EphemeralSearchResults> {
        val result = apiCall {
            apiClientFactory.requireApi().searchEphemeral(
                EphemeralSearchDto(pluginId, sourceId, query),
            )
        }
        if (result is ApiResult.Success) {
            trackCache.putAll(result.value.tracks.map { it.toTrack() })
        }
        return result
    }

    suspend fun setTrackSaved(key: TrackKey, saved: Boolean): ApiResult<Unit> {
        val result = apiCall {
            val response = if (saved) {
                apiClientFactory.requireApi().saveTrack(key.pluginId, key.libraryId, key.trackId)
            } else {
                apiClientFactory.requireApi().unsaveTrack(key.pluginId, key.libraryId, key.trackId)
            }
            if (!response.isSuccessful) throw HttpException(response)
        }
        if (result is ApiResult.Success) trackCache.remove(key)
        return result
    }

    suspend fun setAlbumSaved(idOrIdentity: String, saved: Boolean): ApiResult<Unit> {
        val identity = parseIdentityRef(idOrIdentity)
        if (identity != null && !saved) return ApiResult.Success(Unit)
        return apiCall("album.save") {
            val response = when {
                identity != null -> apiClientFactory.requireApi().saveEphemeralAlbum(
                    identity.pluginId,
                    identity.identifierId,
                    identity.value,
                )

                saved -> apiClientFactory.requireApi().saveAlbum(idOrIdentity)
                else -> apiClientFactory.requireApi().unsaveAlbum(idOrIdentity)
            }
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    suspend fun setArtistSaved(idOrIdentity: String, saved: Boolean): ApiResult<Unit> {
        val identity = parseIdentityRef(idOrIdentity)
        if (identity != null && !saved) return ApiResult.Success(Unit)
        return apiCall("artist.save") {
            val response = when {
                identity != null -> apiClientFactory.requireApi().saveEphemeralArtist(
                    identity.pluginId,
                    identity.identifierId,
                    identity.value,
                )

                saved -> apiClientFactory.requireApi().saveArtist(idOrIdentity)
                else -> apiClientFactory.requireApi().unsaveArtist(idOrIdentity)
            }
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    private fun <T> ApiResult<T>.cacheEphemeralTracks(
        tracksOf: (T) -> List<EphemeralTrack>,
    ): ApiResult<T> {
        if (this is ApiResult.Success) {
            trackCache.putAll(tracksOf(value).map { it.toTrack() })
        }
        return this
    }

    private companion object {
        const val MAX_BATCH = 50
    }
}
