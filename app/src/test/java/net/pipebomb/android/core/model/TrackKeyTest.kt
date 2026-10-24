package net.pipebomb.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackKeyTest {
    @Test
    fun roundTrips() {
        val key = TrackKey("plugin", "library", "track")
        assertEquals(key, TrackKey.parse(key.toString()))
    }

    @Test
    fun keepsColonsInTrackId() {
        val key = TrackKey("plugin", "library", "track:with:colons")
        assertEquals(key, TrackKey.parse(key.toString()))
    }

    @Test
    fun rejectsMalformedKeys() {
        assertNull(TrackKey.parse(""))
        assertNull(TrackKey.parse("a:b"))
        assertNull(TrackKey.parse("a::c"))
    }
}
