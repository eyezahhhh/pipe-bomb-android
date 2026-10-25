package net.pipebomb.android.playback.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSpaceTest {
    @Test
    fun capacityNeverDropsBelowZero() {
        assertEquals(0L, DownloadSpace.availableForDownloads(100L, 200L))
        assertEquals(300L, DownloadSpace.availableForDownloads(500L, 200L))
    }

    @Test
    fun downloadsRequireSpaceStrictlyAboveTheReserve() {
        assertFalse(DownloadSpace.canStartDownload(200L, 200L))
        assertFalse(DownloadSpace.canStartDownload(100L, 200L))
        assertTrue(DownloadSpace.canStartDownload(201L, 200L))
    }
}
