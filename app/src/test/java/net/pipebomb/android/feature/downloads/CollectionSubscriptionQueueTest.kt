package net.pipebomb.android.feature.downloads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionSubscriptionQueueTest {
    @Test
    fun serializesDifferentCollectionsWithoutDroppingEither() = runTest {
        val queue = CollectionSubscriptionQueue(backgroundScope)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        assertTrue(
            queue.enqueue("playlist:one") {
                events += "one:start"
                firstStarted.complete(Unit)
                releaseFirst.await()
                events += "one:end"
            },
        )
        firstStarted.await()
        assertTrue(
            queue.enqueue("playlist:two") {
                events += "two:start"
                secondStarted.complete(Unit)
            },
        )

        runCurrent()
        assertFalse(secondStarted.isCompleted)
        assertEquals(listOf("one:start"), events)
        assertTrue(queue.isBusy.value)

        releaseFirst.complete(Unit)
        secondStarted.await()
        advanceUntilIdle()

        assertEquals(listOf("one:start", "one:end", "two:start"), events)
        assertFalse(queue.isBusy.value)
    }

    @Test
    fun rejectsDuplicateCollectionWhileItIsActive() = runTest {
        val queue = CollectionSubscriptionQueue(backgroundScope)
        val release = CompletableDeferred<Unit>()

        assertTrue(queue.enqueue("playlist:one") { release.await() })
        runCurrent()
        assertFalse(queue.enqueue("playlist:one") {})

        release.complete(Unit)
        runCurrent()
        advanceUntilIdle()
        assertTrue(queue.enqueue("playlist:one") {})
        advanceUntilIdle()
    }
}
