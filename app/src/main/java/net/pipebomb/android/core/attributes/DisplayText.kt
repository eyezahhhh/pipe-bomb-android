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
        formatAlbumArtists(album.artists)

    fun title(artist: Artist): String =
        AttributeReader.string(artist.attributes, "name") ?: "Unknown Artist"

    fun title(playlist: Playlist): String =
        AttributeReader.string(playlist.attributes, "title") ?: "Unnamed Playlist"

    fun artwork(track: Track): Resource? = AttributeReader.resource(track.attributes, "front")

    fun artwork(track: EphemeralTrack): Resource? = AttributeReader.resource(track.attributes, "front")

    fun artwork(album: Album): Resource? = AttributeReader.resource(album.attributes, "front")

    fun artwork(artist: Artist): Resource? = AttributeReader.resource(artist.attributes, "thumb")

    fun artwork(playlist: Playlist): Resource? = AttributeReader.resource(playlist.attributes, "thumb")

    fun durationSeconds(track: Track): Double? =
        AttributeReader.decimal(track.attributes, "duration")

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

    private fun formatAlbumArtists(artists: List<AlbumArtist>?): String {
        if (artists.isNullOrEmpty()) return "Unknown Artist"
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
