package net.pipebomb.android.playback.auto

import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.data.download.DownloadDatabase
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.MediaSessionKeyRegistry
import net.pipebomb.android.playback.PlaybackResolver
import net.pipebomb.android.test.TestFixtures
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoQueueResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: AutoQueueResolver
    private lateinit var downloadDatabase: DownloadDatabase

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = request.path.orEmpty()
                    .substringAfterLast("/tracks/")
                    .substringBefore("/audio")
                    .replace("/", "-")
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"id":"$id","baseUrl":"${server.url("/streaming/$id")}","type":"stream"}""",
                    )
            }
        }
        server.start()
        val factory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
            debugLogging = false,
        )
        downloadDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DownloadDatabase::class.java,
        ).allowMainThreadQueries().build()
        val playbackResolver = PlaybackResolver(
            apiClientFactory = factory,
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            sessionKeyRegistry = MediaSessionKeyRegistry(),
            downloadRepository = DownloadRepository(downloadDatabase, downloadDatabase.downloadDao()),
            storage = StorageLocationRepository(ApplicationProvider.getApplicationContext()),
        )
        resolver = AutoQueueResolver(
            playbackResolver = playbackResolver,
            artworkStore = AutoArtworkStore(factory),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        downloadDatabase.close()
    }

    @Test
    fun resolvesContextQueueAndPreservesStartIndex() = runBlocking {
        val tracks = listOf(
            TestFixtures.track(id = "a", title = "A"),
            TestFixtures.track(id = "b", title = "B"),
            TestFixtures.track(id = "c", title = "C"),
        )

        val queue = resolver.resolve(
            tracks = tracks,
            requestedIndex = 1,
            context = AutoContext.Album("album"),
        )

        assertNotNull(queue)
        assertEquals(3, queue!!.items.size)
        assertEquals(1, queue.startIndex)
        assertEquals("B", queue.tracks[1].title)
        assertEquals(AutoIds.track(AutoContext.Album("album"), tracks[1].key), queue.items[1].mediaId)
        assertTrue(queue.items[0].localConfiguration?.uri.toString().contains("/streaming/"))
    }

    @Test
    fun failedTrackIsSkippedAndStartIndexIsAdjusted() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val isFailure = request.path.orEmpty().contains("/tracks/demo/demo/b/audio")
                if (isFailure) return MockResponse().setResponseCode(500)
                val id = request.path.orEmpty().substringAfterLast("/tracks/").substringBefore("/audio").replace("/", "-")
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"id":"$id","baseUrl":"${server.url("/streaming/$id")}","type":"hls"}""")
            }
        }
        val tracks = listOf(
            TestFixtures.track(id = "a", title = "A"),
            TestFixtures.track(id = "b", title = "B"),
            TestFixtures.track(id = "c", title = "C"),
        )

        val queue = resolver.resolve(tracks, requestedIndex = 1, context = AutoContext.History)

        assertNotNull(queue)
        assertEquals(2, queue!!.items.size)
        assertEquals("C", queue.tracks[queue.startIndex].title)
        assertEquals(MimeTypes.APPLICATION_M3U8, queue.items[0].localConfiguration?.mimeType)
    }
}
