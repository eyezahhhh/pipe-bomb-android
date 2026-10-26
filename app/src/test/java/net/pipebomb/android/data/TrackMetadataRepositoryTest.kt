package net.pipebomb.android.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.data.download.DownloadDatabase
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.playback.ResolvedMedia
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMetadataRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var database: DownloadDatabase
    private lateinit var metadata: TrackMetadataRepository
    private val connectivity = FakeConnectivity()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DownloadDatabase::class.java,
        ).allowMainThreadQueries().build()
        val downloadRepository = DownloadRepository(database, database.downloadDao())
        val catalogRepository = CatalogRepository(
            ApiClientFactory(
                serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
                cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
                json = PipeBombJson,
                debugLogging = false,
            ),
            TrackCache(),
        )
        metadata = TrackMetadataRepository(catalogRepository, downloadRepository, connectivity)
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun localMetadataAndResolutionWorkWithoutNetwork() = runBlocking {
        val track = Track(
            trackId = "track",
            pluginId = "plugin",
            libraryId = "library",
            title = "Offline title",
        )
        val downloadRepository = DownloadRepository(database, database.downloadDao())
        downloadRepository.enqueue(
            track,
            ResolvedMedia(
                trackKey = track.key,
                url = "https://example.test/stream",
                type = ResolvedMedia.MediaType.Stream,
                resolvedAt = 1L,
                sessionId = "session",
            ),
        )

        val local = metadata.local(listOf(track.key))
        assertEquals("Offline title", local[track.key]?.title)
        assertEquals("Offline title", metadata.resolve(track.key)?.title)
        assertTrue(metadata.refresh(listOf(track.key)).isEmpty())
        assertEquals(0, server.requestCount)
    }

    private class FakeConnectivity : ConnectivityStatus {
        override val isOnline = MutableStateFlow(false)
        override val isMetered = MutableStateFlow(false)
    }
}
