package net.pipebomb.android.playback.download

import net.pipebomb.android.data.download.DownloadEntity
import net.pipebomb.android.data.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilePlanTest {
    @Test
    fun emptyIndexDoesNotDeleteCompletedRows() {
        val plan = planReconcile(
            rows = listOf(download("a", DownloadState.Completed)),
            indexedIds = emptySet(),
        )

        assertFalse(plan.indexHealthy)
        assertTrue(plan.missingCompleted.isEmpty())
        assertTrue(plan.failedIncomplete.isEmpty())
        assertTrue(plan.orphans.isEmpty())
    }

    @Test
    fun emptyIndexIsHealthyWhenThereIsNothingToIndex() {
        val plan = planReconcile(
            rows = listOf(download("a", DownloadState.Pending)),
            indexedIds = emptySet(),
        )

        assertTrue(plan.indexHealthy)
        assertTrue(plan.missingCompleted.isEmpty())
        assertTrue(plan.orphans.isEmpty())
    }

    @Test
    fun healthyIndexMarksMissingCompletedRowsInsteadOfDeletingThem() {
        val plan = planReconcile(
            rows = listOf(
                download("a", DownloadState.Completed),
                download("b", DownloadState.Completed),
            ),
            indexedIds = setOf("a"),
        )

        assertTrue(plan.indexHealthy)
        assertEquals(listOf("b"), plan.missingCompleted)
    }

    @Test
    fun healthyIndexMarksInterruptedRowsFailed() {
        val plan = planReconcile(
            rows = listOf(
                download("a", DownloadState.Queued),
                download("b", DownloadState.Paused),
                download("c", DownloadState.Failed),
            ),
            indexedIds = setOf("x"),
        )

        assertEquals(listOf("a", "b"), plan.failedIncomplete)
    }

    @Test
    fun orphanIndexEntriesAreSkippedWhenRowsAreEmpty() {
        val plan = planReconcile(
            rows = emptyList(),
            indexedIds = setOf("a"),
        )

        assertTrue(plan.indexHealthy)
        assertTrue(plan.orphans.isEmpty())
    }

    @Test
    fun orphanIndexEntriesAreRemovedWhenRowsExist() {
        val plan = planReconcile(
            rows = listOf(download("a", DownloadState.Completed)),
            indexedIds = setOf("a", "orphan"),
        )

        assertEquals(listOf("orphan"), plan.orphans)
    }

    private fun download(trackKey: String, state: DownloadState): DownloadEntity =
        DownloadEntity(
            trackKey = trackKey,
            pluginId = "plugin",
            libraryId = "library",
            trackId = trackKey,
            title = trackKey,
            artist = null,
            album = null,
            artworkUrl = null,
            sessionId = "session",
            mediaUrl = "https://example.com/stream",
            state = state.name,
            requestedAt = 0,
        )
}
