package net.pipebomb.android.playback.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.playback.TrackMediaItemFactory

object AutoMediaItems {
    fun category(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artwork: ByteArray? = null,
        browsable: Boolean = true,
        playable: Boolean = false,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .apply { subtitle?.let(::setSubtitle) }
                .setIsBrowsable(browsable)
                .setIsPlayable(playable)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { artwork?.let { setArtworkData(it, null) } }
                .build(),
        )
        .build()

    fun trackQueued(track: Track, context: AutoContext, artwork: ByteArray? = null): MediaItem =
        TrackMediaItemFactory.create(
            track = track,
            mediaId = AutoIds.track(context, track.key),
            artwork = artwork,
        )

    fun trackBrowse(track: Track, context: AutoContext, artwork: ByteArray? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(AutoIds.track(context, track.key))
            .setMediaMetadata(trackMetadata(track, artwork))
            .build()

    fun collectionAction(context: AutoContext, shuffle: Boolean): MediaItem = category(
        mediaId = AutoIds.action(context, shuffle),
        title = if (shuffle) "Shuffle" else "Play",
        subtitle = if (shuffle) "Shuffle all" else "Play all",
        browsable = false,
        playable = true,
    )

    fun album(album: Album, artwork: ByteArray? = null): MediaItem =
        category(
            mediaId = AutoIds.album(album.uuid ?: album.identityKey ?: "unknown"),
            title = DisplayText.title(album),
            subtitle = DisplayText.artists(album),
            artwork = artwork,
            browsable = true,
            playable = false,
        )

    fun artist(artist: Artist, artwork: ByteArray? = null): MediaItem =
        category(
            mediaId = AutoIds.artist(artist.uuid ?: artist.identityKey ?: "unknown"),
            title = DisplayText.title(artist),
            artwork = artwork,
            browsable = true,
            playable = false,
        )

    fun playlist(playlist: Playlist, artwork: ByteArray? = null): MediaItem =
        category(
            mediaId = AutoIds.playlist(playlist.uuid),
            title = DisplayText.title(playlist),
            subtitle = DisplayText.ownerName(playlist),
            artwork = artwork,
            browsable = true,
            playable = false,
        )

    fun savedGroup(type: AutoNode.SavedType): MediaItem = category(
        mediaId = when (type) {
            AutoNode.SavedType.Tracks -> AutoIds.savedTracks()
            AutoNode.SavedType.Albums -> AutoIds.savedAlbums()
            AutoNode.SavedType.Artists -> AutoIds.savedArtists()
        },
        title = when (type) {
            AutoNode.SavedType.Tracks -> "Saved tracks"
            AutoNode.SavedType.Albums -> "Saved albums"
            AutoNode.SavedType.Artists -> "Saved artists"
        },
        browsable = true,
        playable = false,
    )

    private fun trackMetadata(track: Track, artwork: ByteArray?): MediaMetadata {
        val album = AttributeReader.string(track.attributes, "album")
        val duration = DisplayText.durationSeconds(track)
        return MediaMetadata.Builder()
            .setTitle(DisplayText.title(track))
            .setArtist(DisplayText.artists(track))
            .apply {
                album?.let(::setAlbumTitle)
                duration?.let { setDurationMs((it * 1000).toLong()) }
                artwork?.let { setArtworkData(it, null) }
            }
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    }
}
