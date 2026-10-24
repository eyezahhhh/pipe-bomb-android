package net.pipebomb.android.core.attributes

import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.AlbumArtist
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.EphemeralTrack
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackArtist

object DisplayText {
    fun title(track: Track): String =
        AttributeReader.string(track.attributes, "title") ?: track.title

    fun title(track: EphemeralTrack): String =
        AttributeReader.string(track.attributes, "title") ?: track.title

    fun artists(track: Track): String =
        formatArtists(track.artists, AttributeReader.strings(track.attributes, "artist", multiple = true))

    fun artists(track: EphemeralTrack): String =
        formatArtists(track.artists, AttributeReader.strings(track.attributes, "artist", multiple = true))

    fun title(album: Album): String =
        AttributeReader.string(album.attributes, "title") ?: "Unknown Album"

    fun artists(album: Album): String =
        formatAlbumArtists(
            album.artists,
            AttributeReader.strings(album.attributes, "artist", multiple = true),
        )

    fun title(artist: Artist): String =
        AttributeReader.string(artist.attributes, "name") ?: "Unknown Artist"

    fun title(playlist: Playlist): String =
        AttributeReader.string(playlist.attributes, "title") ?: "Unnamed Playlist"

    fun ownerName(playlist: Playlist): String? = when (playlist.owner.type) {
        "user" -> playlist.owner.user?.username
        "bot" -> playlist.owner.botName ?: playlist.owner.botId
        else -> null
    }

    fun artwork(track: Track): Resource? = AttributeReader.resource(track.attributes, "front")

    fun artwork(track: EphemeralTrack): Resource? = AttributeReader.resource(track.attributes, "front")

    fun artwork(album: Album): Resource? = AttributeReader.resource(album.attributes, "front")

    fun artwork(artist: Artist): Resource? = AttributeReader.resource(artist.attributes, "thumb")

    fun artwork(playlist: Playlist): Resource? = AttributeReader.resource(playlist.attributes, "thumb")

    fun durationSeconds(track: Track): Double? =
        AttributeReader.decimal(track.attributes, "duration")

    fun durationSeconds(track: EphemeralTrack): Double? =
        AttributeReader.decimal(track.attributes, "duration")

    fun duration(track: Track): String? = durationSeconds(track)?.let(::formatDuration)

    fun duration(track: EphemeralTrack): String? = durationSeconds(track)?.let(::formatDuration)

    fun formatDuration(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    private fun formatArtists(
        artists: List<TrackArtist>?,
        fallback: List<String>?,
    ): String {
        if (!artists.isNullOrEmpty()) {
            val names = artists.map { DisplayText.title(it.artist) }
            return joinNames(names, artists.map { it.joinPhrase })
        }
        if (!fallback.isNullOrEmpty()) return fallback.joinToString(", ")
        return "Unknown Artist"
    }

    private fun formatAlbumArtists(
        artists: List<AlbumArtist>?,
        fallback: List<String>? = null,
    ): String {
        if (artists.isNullOrEmpty()) {
            if (!fallback.isNullOrEmpty()) return fallback.joinToString(", ")
            return "Unknown Artist"
        }
        val names = artists.map { DisplayText.title(it.artist) }
        return joinNames(names, artists.map { it.joinPhrase })
    }

    private fun joinNames(names: List<String>, joinPhrases: List<String?>): String {
        return buildString {
            names.forEachIndexed { index, name ->
                append(name)
                if (index < names.lastIndex) {
                    append(joinPhrases.getOrNull(index) ?: ", ")
                }
            }
        }
    }
}
