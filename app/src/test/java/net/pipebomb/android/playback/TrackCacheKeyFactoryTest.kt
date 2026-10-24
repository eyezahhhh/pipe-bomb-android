package net.pipebomb.android.playback

import net.pipebomb.android.core.model.TrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackCacheKeyFactoryTest {
    private val registry = MediaSessionKeyRegistry()

    @Test
    fun differentMediaSessionsProduceDifferentCacheKeys() {
        registry.register("session-one", TrackKey("youtube-music", "youtube-music", "one"))
        registry.register("session-two", TrackKey("youtube-music", "youtube-music", "two"))

        val first = registry.cacheKey("/streaming/session-one/hls/playlist.m3u8")
        val second = registry.cacheKey("/streaming/session-two/hls/playlist.m3u8")

        assertNotEquals(first, second)
        assertTrue(first.contains("youtube-music:youtube-music:one"))
        assertTrue(second.contains("youtube-music:youtube-music:two"))
    }

    @Test
    fun hlsSegmentsRemainDistinctWithinASession() {
        registry.register("session-one", TrackKey("youtube-music", "youtube-music", "one"))

        val playlist = registry.cacheKey("/streaming/session-one/hls/playlist.m3u8")
        val segment = registry.cacheKey("/streaming/session-one/hls/segment-1.m4s")

        assertNotEquals(playlist, segment)
    }

    @Test
    fun explicitMediaItemKeyWins() {
        assertEquals(
            "track-key",
            registry.cacheKey("/streaming/session-one/stream", "track-key"),
        )
    }
}
