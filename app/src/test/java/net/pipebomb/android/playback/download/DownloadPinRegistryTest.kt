package net.pipebomb.android.playback.download

import net.pipebomb.android.core.model.TrackKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPinRegistryTest {
    @Test
    fun pinnedTrackProtectsEveryCacheKeyForThatTrack() {
        val pins = DownloadPinRegistry()
        pins.setPinned(listOf(TrackKey("plugin", "library", "track")))

        assertTrue(pins.isPinned("track:plugin:library:track:stream"))
        assertTrue(pins.isPinned("track:plugin:library:track:hls:playlist.m3u8"))
        assertFalse(pins.isPinned("track:plugin:library:other:stream"))
    }

    @Test
    fun replacingPinsUnprotectsRemovedTracks() {
        val pins = DownloadPinRegistry()
        val key = TrackKey("plugin", "library", "track")
        pins.setPinned(listOf(key))
        pins.setPinned(emptyList())

        assertFalse(pins.isPinned("track:plugin:library:track:stream"))
    }
}
