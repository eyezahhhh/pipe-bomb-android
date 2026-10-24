package net.pipebomb.android.data

import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackCacheTest {
    private val key = TrackKey("plugin", "library", "track")
    private val track = Track(
        trackId = "track",
        pluginId = "plugin",
        libraryId = "library",
        title = "Title",
    )

    @Test
    fun returnsCachedTrackBeforeTtl() {
        val cache = TrackCache()
        cache.putAll(listOf(track), now = 1_000L)
        assertEquals(track, cache.get(key, now = 1_000L + 60_000L))
    }

    @Test
    fun expiresCachedTrackAfterTtl() {
        val cache = TrackCache()
        cache.putAll(listOf(track), now = 1_000L)
        assertNull(cache.get(key, now = 1_000L + 10 * 60 * 1000L + 1))
    }

    @Test
    fun clearRemovesEntries() {
        val cache = TrackCache()
        cache.putAll(listOf(track))
        cache.clear()
        assertNull(cache.get(key))
    }
}
