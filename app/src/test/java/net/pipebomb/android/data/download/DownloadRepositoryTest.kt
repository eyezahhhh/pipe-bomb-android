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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        repository = DownloadRepository(database, database.downloadDao())
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
    fun collectionSubscriptionHidesPendingRowsAndReferenceCountsDownloads() = runBlocking {
        val track = track("track")
        repository.subscribeCollection(
            draft = playlistDraft("one"),
            tracks = listOf(track),
            accountUuid = "user",
            accountName = "listener",
        )
        repository.subscribeCollection(
            draft = playlistDraft("two"),
            tracks = listOf(track),
            accountUuid = "user",
            accountName = "listener",
        )

        assertEquals(DownloadState.Pending.name, repository.get(track.key)?.state)
        assertTrue(repository.downloads.first().isEmpty())

        assertTrue(repository.unsubscribeCollection("playlist:one").isEmpty())
        assertNotNull(repository.get(track.key))

        assertEquals(listOf(track.key.toString()), repository.unsubscribeCollection("playlist:two"))
        assertNull(repository.get(track.key))
    }

    @Test
    fun collectionOwnedTrackCannotBeRemovedIndividually() = runBlocking {
        val track = track("track")
        repository.subscribeCollection(
            draft = playlistDraft("one"),
            tracks = listOf(track),
            accountUuid = "user",
            accountName = "listener",
        )
        val item = DownloadItem(repository.get(track.key)!!)

        val result = repository.remove(item)

        assertTrue(result is DownloadRemoveResult.Blocked)
        assertEquals(
            listOf("Playlist one"),
            (result as DownloadRemoveResult.Blocked).collectionTitles,
        )
        assertNotNull(repository.get(track.key))
    }

    @Test
    fun manualDownloadSurvivesCollectionUnsubscribe() = runBlocking {
        val track = track("track")
        val media = ResolvedMedia(
            trackKey = track.key,
            url = "https://example.test/stream",
            type = ResolvedMedia.MediaType.Stream,
            resolvedAt = 1L,
            sessionId = "session",
        )
        repository.enqueue(track, media)
        repository.subscribeCollection(
            draft = playlistDraft("one"),
            tracks = listOf(track),
            accountUuid = "user",
            accountName = "listener",
        )

        repository.unsubscribeCollection("playlist:one")

        assertNotNull(repository.get(track.key))
        assertTrue(repository.get(track.key)?.manual == true)
    }

    @Test
    fun reconcileAddsNewTracksAndReleasesRemovedCollectionTracks() = runBlocking {
        val first = track("first")
        val second = track("second")
        val third = track("third")
        repository.subscribeCollection(
            draft = playlistDraft("one"),
            tracks = listOf(first, second),
            accountUuid = "user",
            accountName = "listener",
        )

        val result = repository.reconcileCollection(
            collectionId = "playlist:one",
            title = "Playlist one",
            artworkUrl = null,
            ownerName = "listener",
            accountUuid = "user",
            accountName = "listener",
            remoteRevision = "2",
            tracks = listOf(second, third),
        )

        assertEquals(listOf(first.key.toString()), result.releasedTrackKeys)
        assertNull(repository.get(first.key))
        assertNotNull(repository.get(second.key))
        assertEquals(DownloadState.Pending.name, repository.get(third.key)?.state)
    }

    @Test
    fun incompleteDownloadsCoverActiveAndPausedButNotFailedOrUnsupported() = runBlocking {
        val active = track("active")
        val paused = track("paused")
        val failed = track("failed")
        val unsupported = track("unsupported")
        listOf(active, paused, failed, unsupported).forEach { repository.enqueue(it, media(it)) }

        repository.updateProgress(active.key.toString(), 1, 10, DownloadState.Queued)
        repository.updateProgress(paused.key.toString(), 1, 10, DownloadState.Paused)
        repository.updateProgress(failed.key.toString(), 1, 10, DownloadState.Failed)
        repository.markUnsupported(unsupported.key.toString(), "HLS")

        assertTrue(repository.hasIncompleteDownloads())
        repository.updateProgress(active.key.toString(), 1, 10, DownloadState.Completed)
        repository.updateProgress(paused.key.toString(), 1, 10, DownloadState.Completed)
        assertFalse(repository.hasIncompleteDownloads())
    }

    @Test
    fun periodicRetryRequeuesManualAndCollectionDownloads() = runBlocking {
        val manual = track("manual")
        val member = track("member")
        val unsupported = track("unsupported")
        listOf(manual, member, unsupported).forEach { repository.enqueue(it, media(it)) }
        repository.subscribeCollection(
            draft = playlistDraft("one"),
            tracks = listOf(member),
            accountUuid = "user",
            accountName = "listener",
        )
        repository.markFailed(manual.key.toString(), "network")
        repository.markFailed(member.key.toString(), "network")
        repository.markUnsupported(unsupported.key.toString(), "HLS")

        assertEquals(2, repository.retryFailedDownloads("user"))

        assertEquals(DownloadState.Pending.name, repository.get(manual.key)?.state)
        assertEquals(DownloadState.Pending.name, repository.get(member.key)?.state)
        assertEquals(DownloadState.Unsupported.name, repository.get(unsupported.key)?.state)
        assertEquals("", repository.get(manual.key)?.sessionId)
        assertEquals(0L, repository.get(manual.key)?.bytesDownloaded)
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

    private fun track(id: String) = Track(
        trackId = id,
        pluginId = "plugin",
        libraryId = "library",
        title = "Track $id",
    )

    private fun media(track: Track) = ResolvedMedia(
        trackKey = track.key,
        url = "https://example.test/stream",
        type = ResolvedMedia.MediaType.Stream,
        resolvedAt = 1L,
        sessionId = "session",
    )

    private fun playlistDraft(id: String) = DownloadCollectionDraft(
        type = DownloadCollectionType.Playlist,
        sourceId = id,
        title = "Playlist $id",
        artworkUrl = null,
        ownerName = "listener",
    )
}
