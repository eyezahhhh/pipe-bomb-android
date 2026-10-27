package net.pipebomb.android.playback.auto

import net.pipebomb.android.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoRecentSnapshotStoreTest {
    @Test
    fun retainsStableSnapshotsByToken() {
        val store = AutoRecentSnapshotStore()
        val first = store.put(TestFixtures.tracks(3), now = 1_000)
        val second = store.put(TestFixtures.tracks(2), now = 2_000)

        assertEquals(first, store.get(first.id, now = 3_000))
        assertEquals(second, store.fresh(now = 3_000))
        assertEquals(second, store.get(second.id, now = 3_000))
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun expiresSnapshots() {
        val store = AutoRecentSnapshotStore()
        val snapshot = store.put(TestFixtures.tracks(2), now = 1_000)

        assertNull(store.get(snapshot.id, now = 1_000 + 10 * 60_000))
    }
}
