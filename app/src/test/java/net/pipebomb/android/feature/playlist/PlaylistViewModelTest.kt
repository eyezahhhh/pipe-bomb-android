package net.pipebomb.android.feature.playlist

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import net.pipebomb.android.core.model.AttributeMap
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.PlaylistTrack
import net.pipebomb.android.core.model.PlaylistVisibility
import net.pipebomb.android.core.model.StringAttribute
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.TrackCache
import net.pipebomb.android.test.MainDispatcherRule
import net.pipebomb.android.test.TestFixtures
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var apiClientFactory: ApiClientFactory
    private lateinit var viewModel: PlaylistViewModel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiClientFactory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
            debugLogging = false,
        )
        val catalogRepository = CatalogRepository(apiClientFactory, TrackCache())
        val authRepository = AuthRepository(
            apiClientFactory,
            PersistentCookieJar(InMemoryCookieStorage()),
        )
        viewModel = PlaylistViewModel(catalogRepository, authRepository)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loadsFirstPageThenFetchesChunkNearViewport() = runBlocking {
        val playlist = playlist(trackCount = 60, initialCount = 2)
        server.enqueue(jsonResponse(playlist))
        server.enqueue(jsonResponse(page(offset = 50, count = 10)))

        viewModel.load(PLAYLIST_UUID)
        waitUntil { !viewModel.state.value.isLoading }

        val initial = viewModel.state.value
        assertEquals(60, initial.totalCount)
        assertEquals(2, initial.tracksByIndex.size)
        assertEquals("Track 0", initial.tracksByIndex[0]?.title)

        viewModel.ensureChunk(1)
        waitUntil { viewModel.state.value.tracksByIndex.containsKey(51) }

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/playlists/$PLAYLIST_UUID?tracks=50"))
        val chunkRequest = server.takeRequest()
        assertEquals("POST", chunkRequest.method)
        assertEquals("/playlists/$PLAYLIST_UUID", chunkRequest.path)
        assertTrue(chunkRequest.body.readUtf8().contains("\"offset\":50"))
        assertEquals("Track 50", viewModel.state.value.tracksByIndex[50]?.title)
    }

    @Test
    fun loadingAllTracksDoesNotReplacePlaylistMetadata() = runBlocking {
        val playlist = playlist(trackCount = 60, initialCount = 2)
        server.enqueue(jsonResponse(playlist))
        server.enqueue(jsonResponse(allTracks()))
        viewModel.load(PLAYLIST_UUID)
        waitUntil { !viewModel.state.value.isLoading }

        val initialTracks = viewModel.state.value.tracksByIndex
        assertNotNull(initialTracks[0]?.attributes)

        val all = viewModel.loadAllTracks()

        assertEquals(60, all.size)
        assertEquals(initialTracks, viewModel.state.value.tracksByIndex)
        assertEquals(2, viewModel.state.value.playlist?.tracks?.size)
    }

    private fun playlist(trackCount: Int, initialCount: Int): Playlist = Playlist(
        uuid = PLAYLIST_UUID,
        visibility = PlaylistVisibility.Public,
        trackCount = trackCount,
        attributes = AttributeMap(
            mapOf("title" to StringAttribute(listOf("Road Trip"), listOf("Road Trip"))),
        ),
        tracks = (0 until initialCount).map { playlistTrack(it) },
    )

    private fun page(offset: Int, count: Int) = (offset until offset + count).map(::playlistTrack)

    private fun allTracks() = (0 until 60).map { index ->
        PlaylistTrack(
            track = TestFixtures.track(title = "Track $index").copy(attributes = null),
        )
    }

    private fun playlistTrack(index: Int) = PlaylistTrack(
        track = TestFixtures.track(title = "Track $index"),
    )

    private fun jsonResponse(value: Any): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            when (value) {
                is Playlist -> PipeBombJson.encodeToString(value)
                else -> PipeBombJson.encodeToString(value as List<PlaylistTrack>)
            },
        )

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    private companion object {
        const val PLAYLIST_UUID = "11111111-1111-4111-8111-111111111111"
    }
}
