package net.pipebomb.android.playback.auto

import androidx.media3.common.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject
import javax.inject.Singleton

data class AutoQueueRequest(
    val tracks: List<Track>,
    val startIndex: Int,
)

internal data class AutoCollectionPage(
    val actionCount: Int,
    val offset: Int,
    val trackCount: Int,
)

internal fun autoCollectionPage(page: Int, pageSize: Int): AutoCollectionPage {
    val actionCount = if (pageSize >= 2) 2 else 0
    val firstPageTrackCount = (pageSize - actionCount).coerceAtLeast(0)
    return AutoCollectionPage(
        actionCount = actionCount,
        offset = if (page == 0) 0 else firstPageTrackCount + (page - 1) * pageSize,
        trackCount = if (page == 0) firstPageTrackCount else pageSize,
    )
}

@Singleton
class AutoLibraryDataSource @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val artworkStore: AutoArtworkStore,
    private val recentSnapshots: AutoRecentSnapshotStore,
) {
    suspend fun children(node: AutoNode, page: Int, pageSize: Int): List<MediaItem> =
        when (node) {
            AutoNode.Root -> rootChildren()
            AutoNode.Recent -> {
                val snapshot = recentSnapshot()
                collectionChildren(
                    tracks = snapshot.tracks,
                    context = AutoContext.Recent(snapshot.id),
                    page = page,
                    pageSize = pageSize,
                )
            }
            AutoNode.Playlists -> playlists()
                .page(page, pageSize)
                .mapPlaylists()
            AutoNode.Saved -> listOf(
                AutoMediaItems.savedGroup(AutoNode.SavedType.Tracks),
                AutoMediaItems.savedGroup(AutoNode.SavedType.Albums),
                AutoMediaItems.savedGroup(AutoNode.SavedType.Artists),
            )
            is AutoNode.SavedGroup -> when (node.type) {
                AutoNode.SavedType.Tracks -> collectionChildren(
                    tracks = allSavedTracks(),
                    context = AutoContext.SavedTracks,
                    page = page,
                    pageSize = pageSize,
                )
                AutoNode.SavedType.Albums -> savedAlbums(page, pageSize).mapAlbums()
                AutoNode.SavedType.Artists -> savedArtists(page, pageSize).mapArtists()
            }
            is AutoNode.Playlist -> collectionChildren(
                tracks = playlistTracks(node.uuid),
                context = AutoContext.Playlist(node.uuid),
                page = page,
                pageSize = pageSize,
            )
            is AutoNode.Album -> collectionChildren(
                tracks = album(node.uuid)?.tracks.orEmpty(),
                context = AutoContext.Album(node.uuid),
                page = page,
                pageSize = pageSize,
            )
            is AutoNode.Artist -> collectionChildren(
                tracks = artist(node.uuid)?.tracks.orEmpty(),
                context = AutoContext.Artist(node.uuid),
                page = page,
                pageSize = pageSize,
            )
            is AutoNode.Track, is AutoNode.Action -> emptyList()
        }

    suspend fun item(node: AutoNode): MediaItem? = when (node) {
        AutoNode.Root -> AutoMediaItems.category(AutoIds.ROOT, "Pipe Bomb", browsable = true)
        AutoNode.Recent -> AutoMediaItems.category(
            AutoIds.RECENT,
            "Recently played",
            browsable = true,
            playable = false,
        )
        AutoNode.Playlists -> AutoMediaItems.category(AutoIds.PLAYLISTS, "Playlists", browsable = true)
        AutoNode.Saved -> AutoMediaItems.category(AutoIds.SAVED, "Saved", browsable = true)
        is AutoNode.SavedGroup -> AutoMediaItems.savedGroup(node.type)
        is AutoNode.Playlist -> playlist(node.uuid)?.let {
            AutoMediaItems.playlist(it, artworkStore.load(artworkOf(it)))
        }
        is AutoNode.Album -> album(node.uuid)?.let {
            AutoMediaItems.album(it, artworkStore.load(artworkOf(it)))
        }
        is AutoNode.Artist -> artist(node.uuid)?.let {
            AutoMediaItems.artist(it, artworkStore.load(artworkOf(it)))
        }
        is AutoNode.Track -> tracksByKeys(listOf(node.key)).firstOrNull()?.let { track ->
            AutoMediaItems.trackBrowse(
                track = track,
                context = node.context,
                artwork = artworkStore.load(artworkOf(track)),
            )
        }
        is AutoNode.Action -> AutoMediaItems.collectionAction(node.context, node.shuffle)
    }

    suspend fun queueRequest(node: AutoNode): AutoQueueRequest? {
        val context = when (node) {
            is AutoNode.Track -> node.context
            is AutoNode.Action -> node.context
            else -> AutoIds.contextFor(node)
        } ?: return null
        val tracks = tracksFor(context).let { all ->
            if (node is AutoNode.Action && node.shuffle) all.shuffled() else all
        }
        val requestedKey = (node as? AutoNode.Track)?.key
        val index = requestedKey?.let { key ->
            tracks.indexOfFirst { it.key == key }
        } ?: 0
        if (tracks.isEmpty()) return null
        return AutoQueueRequest(tracks, index.coerceAtLeast(0))
    }

    suspend fun tracksFor(context: AutoContext): List<Track> = when (context) {
        is AutoContext.Recent -> recentTracks(context.snapshotId)
        is AutoContext.Album -> album(context.uuid)?.tracks.orEmpty()
        is AutoContext.Playlist -> playlistTracks(context.uuid)
        is AutoContext.Artist -> artist(context.uuid)?.tracks.orEmpty()
        AutoContext.SavedTracks -> allSavedTracks()
        AutoContext.History -> allHistoryTracks()
        is AutoContext.Search -> searchTracks(context.query)
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        AutoMediaItems.category(
            AutoIds.RECENT,
            "Recently played",
            browsable = true,
            playable = false,
        ),
        AutoMediaItems.category(AutoIds.PLAYLISTS, "Playlists", browsable = true),
        AutoMediaItems.category(AutoIds.SAVED, "Saved", browsable = true),
    )

    private suspend fun recentSnapshot(): AutoRecentSnapshotStore.Snapshot {
        recentSnapshots.fresh()?.let { return it }
        val tracks = fetchRecentTracks()
        return recentSnapshots.put(tracks)
    }

    private suspend fun recentTracks(snapshotId: String? = null): List<Track> {
        snapshotId?.let { id ->
            recentSnapshots.get(id)?.let { return it.tracks }
        }
        return recentSnapshot().tracks
    }

    private suspend fun fetchRecentTracks(): List<Track> {
        val unique = LinkedHashMap<String, Track>()
        var page = 0
        while (unique.size < MAX_RECENT) {
            val chunk = historyTracks(page = page, pageSize = PAGE_SIZE)
            if (chunk.isEmpty()) break
            chunk.forEach { unique.putIfAbsent(it.key.toString(), it) }
            if (chunk.size < PAGE_SIZE) break
            page++
        }
        return unique.values.take(MAX_RECENT)
    }

    private suspend fun collectionChildren(
        tracks: List<Track>,
        context: AutoContext,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        val pageBounds = autoCollectionPage(page, pageSize)
        val pageTracks = tracks.drop(pageBounds.offset).take(pageBounds.trackCount)
        val actions = if (page == 0 && pageBounds.actionCount == 2) {
            listOf(
                AutoMediaItems.collectionAction(context, shuffle = false),
                AutoMediaItems.collectionAction(context, shuffle = true),
            )
        } else {
            emptyList()
        }
        return actions + pageTracks.mapTracks(context)
    }

    private suspend fun playlists(): List<Playlist> {
        val own = (catalogRepository.ownPlaylists() as? ApiResult.Success)?.value.orEmpty()
        val public = (catalogRepository.publicPlaylists() as? ApiResult.Success)?.value.orEmpty()
        return (own + public).distinctBy { it.uuid }
    }

    private suspend fun playlist(uuid: String): Playlist? =
        (catalogRepository.playlist(uuid, tracks = 200) as? ApiResult.Success)?.value

    private suspend fun playlistTracks(uuid: String): List<Track> =
        (catalogRepository.allPlaylistTracks(uuid) as? ApiResult.Success)
            ?.value
            ?.map { it.track }
            .orEmpty()

    private suspend fun album(uuid: String): Album? =
        (catalogRepository.album(uuid) as? ApiResult.Success)?.value

    private suspend fun artist(uuid: String): Artist? =
        (catalogRepository.artist(uuid) as? ApiResult.Success)?.value

    private suspend fun savedTracks(page: Int, pageSize: Int): List<Track> =
        (catalogRepository.savedTracks(page = page + 1, pageSize = pageSize) as? ApiResult.Success)
            ?.value?.tracks.orEmpty()

    private suspend fun savedAlbums(page: Int, pageSize: Int): List<Album> =
        (catalogRepository.savedAlbums(page = page + 1, pageSize = pageSize) as? ApiResult.Success)
            ?.value?.albums.orEmpty()

    private suspend fun savedArtists(page: Int, pageSize: Int): List<Artist> =
        (catalogRepository.savedArtists(page = page + 1, pageSize = pageSize) as? ApiResult.Success)
            ?.value?.artists.orEmpty()

    private suspend fun historyTracks(page: Int, pageSize: Int): List<Track> =
        (catalogRepository.history(page = page + 1, pageSize = pageSize) as? ApiResult.Success)
            ?.value?.entries.orEmpty().mapNotNull { it.track }

    private suspend fun allSavedTracks(): List<Track> = pagedTracks(Int.MAX_VALUE) { page ->
        savedTracks(page = page, pageSize = PAGE_SIZE)
    }

    private suspend fun allHistoryTracks(): List<Track> = pagedTracks(Int.MAX_VALUE) { page ->
        historyTracks(page = page, pageSize = PAGE_SIZE)
    }

    suspend fun searchItems(query: String, page: Int, pageSize: Int): List<MediaItem> {
        val context = AutoContext.Search(query)
        val tracks = searchTracks(query).drop(page * pageSize).take(pageSize)
        return tracks.mapTracks(context)
    }

    suspend fun tracksByKeys(keys: List<TrackKey>): List<Track> =
        (catalogRepository.tracks(keys) as? ApiResult.Success)?.value.orEmpty()

    private suspend fun searchTracks(query: String): List<Track> =
        (catalogRepository.search(query) as? ApiResult.Success)?.value?.tracks.orEmpty().take(MAX_SEARCH_RESULTS)

    private suspend fun pagedTracks(limit: Int, fetch: suspend (Int) -> List<Track>): List<Track> {
        val result = mutableListOf<Track>()
        var page = 0
        while (result.size < limit) {
            val chunk = fetch(page)
            if (chunk.isEmpty()) break
            result += chunk
            page++
        }
        return result.take(limit)
    }

    private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> =
        drop(page * pageSize).take(pageSize)

    private suspend fun List<Track>.mapTracks(context: AutoContext): List<MediaItem> =
        coroutineScope {
            val semaphore = Semaphore(ART_CONCURRENCY)
            map { track ->
                async {
                    val bytes = semaphore.withPermit { artworkStore.load(artworkOf(track)) }
                    AutoMediaItems.trackBrowse(track, context, bytes)
                }
            }.awaitAll()
        }

    private suspend fun List<Playlist>.mapPlaylists(): List<MediaItem> = coroutineScope {
        val semaphore = Semaphore(ART_CONCURRENCY)
        map { playlist ->
            async {
                val bytes = semaphore.withPermit { artworkStore.load(artworkOf(playlist)) }
                AutoMediaItems.playlist(playlist, bytes)
            }
        }.awaitAll()
    }

    private suspend fun List<Album>.mapAlbums(): List<MediaItem> = coroutineScope {
        val semaphore = Semaphore(ART_CONCURRENCY)
        map { album ->
            async {
                val bytes = semaphore.withPermit { artworkStore.load(artworkOf(album)) }
                AutoMediaItems.album(album, bytes)
            }
        }.awaitAll()
    }

    private suspend fun List<Artist>.mapArtists(): List<MediaItem> = coroutineScope {
        val semaphore = Semaphore(ART_CONCURRENCY)
        map { artist ->
            async {
                val bytes = semaphore.withPermit { artworkStore.load(artworkOf(artist)) }
                AutoMediaItems.artist(artist, bytes)
            }
        }.awaitAll()
    }

    private fun artworkOf(track: Track) = AttributeReader.resource(track.attributes, "front")
    private fun artworkOf(album: Album) = AttributeReader.resource(album.attributes, "front")
    private fun artworkOf(artist: Artist) = AttributeReader.resource(artist.attributes, "thumb")
    private fun artworkOf(playlist: Playlist) = AttributeReader.resource(playlist.attributes, "thumb")

    private companion object {
        const val MAX_SEARCH_RESULTS = 200
        const val MAX_RECENT = 100
        private const val PAGE_SIZE = 50
        private const val ART_CONCURRENCY = 4
    }
}
