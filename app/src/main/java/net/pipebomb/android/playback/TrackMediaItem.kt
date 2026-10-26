package net.pipebomb.android.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import java.util.Base64

object TrackMediaUri {
    private const val SCHEME = "pipebomb"
    private const val HOST = "track"

    fun build(key: TrackKey): Uri {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(key.toString().toByteArray(Charsets.UTF_8))
        return Uri.parse("$SCHEME://$HOST/$encoded")
    }

    fun parse(uri: Uri): TrackKey? {
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        val encoded = uri.pathSegments.firstOrNull() ?: return null
        return runCatching {
            val raw = Base64.getUrlDecoder().decode(encoded).toString(Charsets.UTF_8)
            TrackKey.parse(raw)
        }.getOrNull()
    }
}

object TrackMediaItemFactory {
    fun create(
        track: Track,
        mediaId: String = track.key.toString(),
        artwork: ByteArray? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(AttributeReader.string(track.attributes, "title") ?: track.title)
            .apply {
                val artists = track.artists
                    ?.mapNotNull { entry -> AttributeReader.string(entry.artist.attributes, "name") }
                    ?.takeIf { it.isNotEmpty() }
                    ?: AttributeReader.strings(track.attributes, "artist", multiple = true)
                artists?.joinToString(", ")?.let(::setArtist)
                AttributeReader.string(track.attributes, "album")?.let(::setAlbumTitle)
                AttributeReader.decimal(track.attributes, "duration")
                    ?.let { setDurationMs((it * 1000).toLong()) }
                artwork?.let { setArtworkData(it, null) }
                    ?: AttributeReader.resource(track.attributes, "front")?.url
                        ?.let { setArtworkUri(Uri.parse(it)) }
            }
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setUri(TrackMediaUri.build(track.key))
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun withResolvedSource(item: MediaItem, media: ResolvedMedia): MediaItem =
        item.buildUpon()
            .setUri(Uri.parse(media.url))
            .apply {
                if (media.type == ResolvedMedia.MediaType.Hls) {
                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                } else {
                    setCustomCacheKey(TrackCacheKeyFactory.streamKey(media.trackKey.toString()))
                }
            }
            .build()
}
