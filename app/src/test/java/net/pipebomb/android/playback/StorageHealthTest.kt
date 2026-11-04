package net.pipebomb.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageHealthTest {
    @Test
    fun healthyStorageHasNoIssue() {
        val health = StorageHealth(
            selectedId = "internal",
            label = "Internal storage",
            cacheDir = "/data/media",
            selectedDir = "/data/media",
            fallback = false,
            mismatch = false,
            available = true,
            freeBytes = 1,
            totalBytes = 2,
        )

        assertNull(health.issue)
    }

    @Test
    fun missingSelectedStorageWinsOverMismatch() {
        val health = StorageHealth(
            selectedId = "external:card",
            label = "SD card",
            cacheDir = "/data/media",
            selectedDir = "/data/fallback",
            fallback = true,
            mismatch = true,
            available = false,
            freeBytes = 0,
            totalBytes = 0,
        )

        assertEquals(
            "Selected storage is unavailable. Reconnect it and restart Pipe Bomb.",
            health.issue,
        )
    }

    @Test
    fun changedAvailableStorageAsksForRestart() {
        val health = StorageHealth(
            selectedId = "external:card",
            label = "SD card",
            cacheDir = "/data/media",
            selectedDir = "/storage/card",
            fallback = false,
            mismatch = true,
            available = false,
            freeBytes = 1,
            totalBytes = 2,
        )

        assertEquals("Storage changed since startup. Restart Pipe Bomb to use it.", health.issue)
    }

    @Test
    fun errorWinsOverEverything() {
        val health = StorageHealth(
            selectedId = "external:card",
            label = "SD card",
            cacheDir = null,
            selectedDir = null,
            fallback = true,
            mismatch = true,
            available = false,
            freeBytes = 0,
            totalBytes = 0,
            error = "Storage check timed out.",
        )

        assertEquals("Storage check timed out.", health.issue)
    }
}
