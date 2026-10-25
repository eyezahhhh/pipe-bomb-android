package net.pipebomb.android.test

import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.AttributeMap
import net.pipebomb.android.core.model.DecimalAttribute
import net.pipebomb.android.core.model.StringAttribute
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackArtist
import net.pipebomb.android.playback.PlaybackUiState
import net.pipebomb.android.playback.RepeatMode

object TestFixtures {
    fun track(
        id: String = "track-1",
        title: String = "Midnight Drive",
        artist: String = "Nova",
        durationSeconds: Double = 245.0,
    ): Track = Track(
        uuid = "uuid-",
        trackId = id,
        pluginId = "demo",
        libraryId = "demo",
        title = title,
        attributes = AttributeMap(
            mapOf(
                "title" to StringAttribute(listOf(title), listOf(title)),
                "artist" to StringAttribute(listOf(artist), listOf(artist)),
                "duration" to DecimalAttribute(listOf(durationSeconds)),
            ),
        ),
        artists = listOf(
            TrackArtist(
                artist = Artist(
                    uuid = "artist-uuid",
                    attributes = AttributeMap(
                        mapOf("name" to StringAttribute(listOf(artist), listOf(artist))),
                    ),
                ),
            ),
        ),
    )

    fun tracks(count: Int = 3): List<Track> = (0 until count).map { index ->
        track(
            id = "track-$index",
            title = if (index == 1) "Midnight Drive" else "Track $index",
        )
    }

    fun playback(
        isPlaying: Boolean = true,
        isBuffering: Boolean = false,
        positionMs: Long = 62_000,
        durationMs: Long = 245_000,
        shuffle: Boolean = false,
        repeat: RepeatMode = RepeatMode.Off,
    ): PlaybackUiState = PlaybackUiState(
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = positionMs,
        durationMs = durationMs,
        shuffle = shuffle,
        repeat = repeat,
    )
}
