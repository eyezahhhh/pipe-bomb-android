package net.pipebomb.android.feature.library

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import net.pipebomb.android.core.model.PlaybackHistoryEntry
import net.pipebomb.android.core.model.PlaybackHistoryPage
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.TrackCache
import net.pipebomb.android.test.MainDispatcherRule
import net.pipebomb.android.test.TestFixtures
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var viewModel: LibraryViewModel
    private val historyResponses = ConcurrentLinkedQueue<MockResponse>()
    private val historyRequests = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/playback-history")) {
                    historyRequests += path
                    return historyResponses.poll() ?: MockResponse().setResponseCode(500)
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]")
            }
        }
        server.start()
        val apiClientFactory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
            debugLogging = false,
        )
        viewModel = LibraryViewModel(CatalogRepository(apiClientFactory, TrackCache()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun historyLoadsOnlyWhenRequestedThenFetchesPageNearViewport() = runBlocking {
        assertTrue(viewModel.history.value.isLoading)
        assertTrue(historyRequests.isEmpty())

        historyResponses += jsonResponse(historyPage(page = 1, offset = 0, count = 2, total = 120))
        viewModel.ensureHistoryLoaded()
        waitUntil { !viewModel.history.value.isLoading }

        assertEquals(120, viewModel.history.value.total)
        assertEquals("Track 0", viewModel.history.value.tracksByIndex[0]?.title)
        assertEquals("/playback-history/me?pageSize=50&page=1", historyRequests.single())

        historyResponses += jsonResponse(historyPage(page = 2, offset = 50, count = 2, total = 120))
        viewModel.ensureHistoryChunk(1)
        waitUntil { viewModel.history.value.tracksByIndex.containsKey(51) }

        assertEquals("/playback-history/me?pageSize=50&page=2", historyRequests[1])
        assertEquals("Track 50", viewModel.history.value.tracksByIndex[50]?.title)
        assertEquals("Track 51", viewModel.history.value.tracksByIndex[51]?.title)
    }

    @Test
    fun historyChunkIsNotFetchedTwice() = runBlocking {
        historyResponses += jsonResponse(historyPage(page = 1, offset = 0, count = 2, total = 120))
        viewModel.ensureHistoryLoaded()
        waitUntil { !viewModel.history.value.isLoading }

        historyResponses += jsonResponse(historyPage(page = 2, offset = 50, count = 1, total = 120))
        viewModel.ensureHistoryChunk(1)
        waitUntil { viewModel.history.value.loadedChunks.contains(1) }

        viewModel.ensureHistoryChunk(1)
        delay(100)
        assertEquals(2, historyRequests.size)
    }

    private fun historyPage(page: Int, offset: Int, count: Int, total: Int): PlaybackHistoryPage =
        PlaybackHistoryPage(
            total = total,
            entries = (offset until offset + count).map { index ->
                PlaybackHistoryEntry(
                    trackUuid = "uuid-$index",
                    pluginId = "demo",
                    clientName = "Test",
                    track = TestFixtures.track(title = "Track $index"),
                )
            },
        )

    private fun jsonResponse(value: PlaybackHistoryPage): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(PipeBombJson.encodeToString(value))

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }
}
