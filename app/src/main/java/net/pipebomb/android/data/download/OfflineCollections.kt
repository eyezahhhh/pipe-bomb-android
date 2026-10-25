package net.pipebomb.android.data.download

import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.AttributeMap
import net.pipebomb.android.core.model.BufferAttribute
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.PlaylistOwner
import net.pipebomb.android.core.model.PlaylistTrack
import net.pipebomb.android.core.model.PlaylistVisibility
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.model.StringAttribute
import net.pipebomb.android.core.model.User

suspend fun DownloadRepository.offlineAlbum(id: String): Album? {
    val collection = getCollection(DownloadCollectionType.Album.collectionId(id)) ?: return null
    val tracks = getCollectionTracks(collection.id).map { it.toTrack() }
    val attributes = buildMap {
        put("title", StringAttribute(listOf(collection.title), listOf(collection.title)))
        collection.ownerName?.let {
            put("artist", StringAttribute(listOf(it), listOf(it)))
        }
        collection.artworkUrl?.let {
            put("front", BufferAttribute(listOf(Resource(url = it))))
        }
    }
    return Album(
        uuid = collection.sourceId,
        attributes = AttributeMap(attributes),
        tracks = tracks,
    )
}

suspend fun DownloadRepository.offlinePlaylist(id: String): Playlist? {
    val collection = getCollection(DownloadCollectionType.Playlist.collectionId(id)) ?: return null
    val tracks = getCollectionTracks(collection.id).map { it.toTrack() }
    val attributes = buildMap {
        put("title", StringAttribute(listOf(collection.title), listOf(collection.title)))
        collection.artworkUrl?.let {
            put("thumb", BufferAttribute(listOf(Resource(url = it))))
        }
    }
    return Playlist(
        uuid = collection.sourceId,
        owner = PlaylistOwner(
            type = "user",
            userUuid = collection.accountUuid,
            user = collection.accountUuid?.let {
                User(uuid = it, username = collection.accountName.orEmpty())
            },
        ),
        dateModified = null,
        visibility = PlaylistVisibility.Private,
        attributes = AttributeMap(attributes),
        tracks = tracks.map { PlaylistTrack(track = it) },
        trackCount = tracks.size,
    )
}
