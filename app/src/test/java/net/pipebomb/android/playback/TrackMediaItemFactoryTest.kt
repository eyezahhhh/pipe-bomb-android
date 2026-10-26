package net.pipebomb.android.playback

import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMediaItemFactoryTest {
    private val key = TrackKey("demo", "demo", "track-1")

    @Test
    fun queueItemsUseStableSyntheticUris() {
        val track = TestFixtures.track(id = key.trackId)

        val item = TrackMediaItemFactory.create(track, mediaId = "browse-id")

        assertEquals("browse-id", item.mediaId)
        assertEquals(TrackMediaUri.build(key), item.localConfiguration?.uri)
        assertEquals("Midnight Drive", item.mediaMetadata.title)
        assertEquals("Nova", item.mediaMetadata.artist)
    }

    @Test
    fun resolvedProgressiveSourceUsesCacheKeyInsteadOfSessionUrl() {
        val item = TrackMediaItemFactory.create(TestFixtures.track(id = key.trackId))
        val resolved = ResolvedMedia(
            trackKey = key,
            url = "https://server/streaming/session/stream",
            type = ResolvedMedia.MediaType.Stream,
            resolvedAt = 1L,
            sessionId = "session",
        )

        val source = TrackMediaItemFactory.withResolvedSource(item, resolved)

        assertEquals("https://server/streaming/session/stream", source.localConfiguration?.uri.toString())
        assertEquals("track:demo:demo:track-1:stream", source.localConfiguration?.customCacheKey)
        assertNull(source.localConfiguration?.mimeType)
    }

    @Test
    fun resolvedHlsSourceUsesHlsMimeType() {
        val item = TrackMediaItemFactory.create(TestFixtures.track(id = key.trackId))
        val resolved = ResolvedMedia(
            trackKey = key,
            url = "https://server/streaming/session/hls/playlist.m3u8",
            type = ResolvedMedia.MediaType.Hls,
            resolvedAt = 1L,
            sessionId = "session",
        )

        val source = TrackMediaItemFactory.withResolvedSource(item, resolved)

        assertEquals(MimeTypes.APPLICATION_M3U8, source.localConfiguration?.mimeType)
    }
}
