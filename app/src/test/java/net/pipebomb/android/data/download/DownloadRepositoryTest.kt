package net.pipebomb.android.data.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.playback.ResolvedMedia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryTest {
    private lateinit var database: DownloadDatabase
    private lateinit var repository: DownloadRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DownloadDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = DownloadRepository(database.downloadDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun enqueueStoresMetadataAndRequiresCompletionForOfflineLookup() = runBlocking {
        val track = Track(
            trackId = "track",
            pluginId = "plugin",
            libraryId = "library",
            title = "Title",
        )
        val media = ResolvedMedia(
            trackKey = track.key,
            url = "https://example.test/streaming/session/stream",
            type = ResolvedMedia.MediaType.Stream,
            resolvedAt = 1L,
            sessionId = "session",
        )

        repository.enqueue(track, media)

        val queued = repository.get(track.key)
        assertNotNull(queued)
        assertEquals(DownloadState.Queued.name, queued?.state)
        assertEquals("Title", queued?.title)
        assertNull(repository.completed(track.key))

        repository.updateProgress(
            trackKey = track.key.toString(),
            bytesDownloaded = 100,
            bytesTotal = 100,
            state = DownloadState.Completed,
        )

        assertEquals("session", repository.completed(track.key)?.sessionId)
        assertEquals(1, repository.downloads.first().size)
    }

    @Test
    fun retryKeepsDisplayMetadataAndResetsProgress() = runBlocking {
        val track = Track(
            trackId = "track",
            pluginId = "plugin",
            libraryId = "library",
            title = "Title",
        )
        val first = ResolvedMedia(
            trackKey = track.key,
            url = "https://example.test/first",
            type = ResolvedMedia.MediaType.Stream,
            resolvedAt = 1L,
            sessionId = "first",
        )
        repository.enqueue(track, first)
        repository.updateProgress(
            trackKey = track.key.toString(),
            bytesDownloaded = 100,
            bytesTotal = 200,
            state = DownloadState.Failed,
            error = "failed",
        )

        repository.requeue(
            track.key,
            first.copy(
                url = "https://example.test/second",
                sessionId = "second",
            ),
        )

        val retried = repository.get(track.key)
        assertEquals(DownloadState.Queued.name, retried?.state)
        assertEquals("Title", retried?.title)
        assertEquals("second", retried?.sessionId)
        assertEquals(0L, retried?.bytesDownloaded)
        assertEquals(null, retried?.lastError)
    }
}
