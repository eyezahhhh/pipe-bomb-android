package net.pipebomb.android.playback.auto

import net.pipebomb.android.core.model.TrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoIdsTest {
    private val key = TrackKey("youtube-music", "youtube-music", "abc:123")

    @Test
    fun rootIdsRoundTrip() {
        assertEquals(AutoNode.Root, AutoIds.parse(AutoIds.ROOT))
        assertEquals(AutoNode.Recent, AutoIds.parse(AutoIds.RECENT))
        assertEquals(AutoNode.Playlists, AutoIds.parse(AutoIds.PLAYLISTS))
        assertEquals(AutoNode.Saved, AutoIds.parse(AutoIds.SAVED))
    }

    @Test
    fun parentIdsRoundTrip() {
        assertEquals(AutoNode.Playlist("p1"), AutoIds.parse(AutoIds.playlist("p1")))
        assertEquals(AutoNode.Album("a1"), AutoIds.parse(AutoIds.album("a1")))
        assertEquals(AutoNode.Artist("ar1"), AutoIds.parse(AutoIds.artist("ar1")))
        assertEquals(
            AutoNode.SavedGroup(AutoNode.SavedType.Tracks),
            AutoIds.parse(AutoIds.savedTracks()),
        )
    }

    @Test
    fun trackIdsRoundTripWithEveryContext() {
        val contexts = listOf(
            AutoContext.Album("album"),
            AutoContext.Playlist("playlist"),
            AutoContext.Artist("artist"),
            AutoContext.Recent("snapshot"),
            AutoContext.SavedTracks,
            AutoContext.History,
            AutoContext.Search("space & symbols"),
        )
        for (context in contexts) {
            val id = AutoIds.track(context, key)
            val parsed = AutoIds.parse(id) as AutoNode.Track
            assertEquals(context, parsed.context)
            assertEquals(key, parsed.key)
        }
    }

    @Test
    fun malformedIdsAreRejected() {
        assertNull(AutoIds.parse(""))
        assertNull(AutoIds.parse("pb:unknown"))
        assertNull(AutoIds.parse("pb:track:not-base64"))
        assertNull(AutoIds.parse("pb:playlist"))
    }
}
