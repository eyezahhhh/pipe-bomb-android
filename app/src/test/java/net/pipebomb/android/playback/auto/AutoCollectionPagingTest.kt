package net.pipebomb.android.playback.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCollectionPagingTest {
    @Test
    fun firstPageReservesTwoSlotsForCollectionActions() {
        val page = autoCollectionPage(page = 0, pageSize = 10)

        assertEquals(2, page.actionCount)
        assertEquals(0, page.offset)
        assertEquals(8, page.trackCount)
    }

    @Test
    fun laterPagesContinueAfterTheShorterFirstPage() {
        assertEquals(8, autoCollectionPage(page = 1, pageSize = 10).offset)
        assertEquals(10, autoCollectionPage(page = 1, pageSize = 10).trackCount)
        assertEquals(18, autoCollectionPage(page = 2, pageSize = 10).offset)
    }

    @Test
    fun tinyPagesStillReturnAtMostTheRequestedPageSize() {
        val first = autoCollectionPage(page = 0, pageSize = 1)
        val second = autoCollectionPage(page = 1, pageSize = 1)

        assertEquals(0, first.actionCount)
        assertEquals(1, first.trackCount)
        assertEquals(1, second.offset)
        assertEquals(1, second.trackCount)
    }
}
