package net.pipebomb.android.playback

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.pipebomb.android.core.model.TrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMediaUriTest {
    @Test
    fun roundTripsTrackKeysWithReservedCharacters() {
        val key = TrackKey("plugin/one", "library two", "track:with/colons and spaces")

        val uri = TrackMediaUri.build(key)

        assertEquals("pipebomb", uri.scheme)
        assertEquals("track", uri.host)
        assertEquals(key, TrackMediaUri.parse(uri))
    }

    @Test
    fun rejectsOtherUrisAndMalformedTrackKeys() {
        assertNull(TrackMediaUri.parse(Uri.parse("https://example.com/track/abc")))
        assertNull(TrackMediaUri.parse(Uri.parse("pipebomb://other/abc")))
        assertNull(TrackMediaUri.parse(Uri.parse("pipebomb://track/")))
        assertNull(TrackMediaUri.parse(Uri.parse("pipebomb://track/not-base64!")))
    }
}
