package net.pipebomb.android.playback.auto

import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.data.download.DownloadDao
import net.pipebomb.android.data.download.DownloadEntity
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
        val playbackResolver = PlaybackResolver(
            apiClientFactory = factory,
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            sessionKeyRegistry = MediaSessionKeyRegistry(),
            downloadRepository = DownloadRepository(FakeDownloadDao()),
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

private class FakeDownloadDao : DownloadDao {
    override fun observeAll(): Flow<List<DownloadEntity>> = flowOf(emptyList())

    override fun observe(trackKey: String): Flow<DownloadEntity?> = flowOf(null)

    override suspend fun get(trackKey: String): DownloadEntity? = null

    override suspend fun getAll(): List<DownloadEntity> = emptyList()

    override suspend fun upsert(entity: DownloadEntity) = Unit

    override suspend fun delete(trackKey: String) = Unit

    override suspend fun deleteAll() = Unit

    override fun observeTotalBytes(): Flow<Long> = flowOf(0L)

    override suspend fun totalBytes(): Long = 0L
}
