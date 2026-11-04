package net.pipebomb.android.feature.artist

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.TrackCache
import net.pipebomb.android.test.MainDispatcherRule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var viewModel: ArtistViewModel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val factory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
        )
        viewModel = ArtistViewModel(
            catalogRepository = CatalogRepository(factory, TrackCache()),
            connectivity = object : ConnectivityStatus {
                override val isOnline = MutableStateFlow(true)
                override val isMetered = MutableStateFlow(false)
            },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun identityArtistLoadsEphemeralContentWithoutSourceTabs() = runBlocking {
        server.enqueue(jsonResponse("""{"attributes":{},"identities":[{"pluginId":"plugin","identityId":"identifier","value":"value","ordinal":0}]}"""))
        server.enqueue(jsonResponse(contentJson("source-a", "Source A")))

        viewModel.load("plugin~identifier~value")
        waitUntil { !viewModel.state.value.isLoading && viewModel.state.value.ephemeral.content != null }

        val state = viewModel.state.value
        assertNotNull(state.artist)
        assertTrue(state.ephemeral.isIdentity)
        assertTrue(state.ephemeral.sources.isEmpty())
        assertEquals("source-a", state.ephemeral.content?.source?.id)
        assertEquals("Ephemeral track", state.ephemeral.content?.tracks?.single()?.title)

        val artistRequest = server.takeRequest()
        assertEquals("GET", artistRequest.method)
        assertEquals("/artists/plugin/identifier/value", artistRequest.path)
        val contentRequest = server.takeRequest()
        assertEquals("POST", contentRequest.method)
        assertEquals("/artists/plugin/identifier/value", contentRequest.path)
    }

    @Test
    fun uuidArtistSelectsFirstSourceAndCanSwitchSources() = runBlocking {
        server.enqueue(jsonResponse("""{"uuid":"artist-uuid"}"""))
        server.enqueue(jsonResponse("""[{"id":"source-a","pluginId":"plugin","name":"Source A"},{"id":"source-b","pluginId":"plugin","name":"Source B"}]"""))
        server.enqueue(jsonResponse(contentJson("source-a", "Source A")))
        server.enqueue(jsonResponse(contentJson("source-b", "Source B")))

        viewModel.load("artist-uuid")
        waitUntil { viewModel.state.value.ephemeral.content != null }

        val sources = viewModel.state.value.ephemeral.sources
        assertEquals(2, sources.size)
        assertEquals("source-a", viewModel.state.value.ephemeral.selectedSource?.id)
        assertEquals("Source A", viewModel.state.value.ephemeral.content?.source?.name)

        viewModel.selectEphemeralSource(sources[1])
        waitUntil { viewModel.state.value.ephemeral.content?.source?.id == "source-b" }

        assertEquals("Source B", viewModel.state.value.ephemeral.content?.source?.name)
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(10)
        }
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun contentJson(sourceId: String, sourceName: String): String = """
        {
          "source": {"id":"$sourceId","pluginId":"plugin","name":"$sourceName"},
          "tracks": [{
            "uuid": null,
            "trackId": "track",
            "pluginId": "plugin",
            "libraryId": "library",
            "title": "Ephemeral track"
          }],
          "albums": []
        }
    """.trimIndent()
}
