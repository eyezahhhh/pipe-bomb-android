package net.pipebomb.android.core.net

import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.TrackCache
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var factory: ApiClientFactory

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        factory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
            debugLogging = false,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun setupStatusIgnoresUnknownFields() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"needsSetup":false,"futureField":"ignored"}"""),
        )

        val status = factory.requireApi().getSetupStatus()

        assertTrue(!status.needsSetup)
    }

    @Test
    fun loginStoresCookieAndSendsItOnNextRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "auth_token=test-token; Path=/; HttpOnly")
                .setBody("""{"uuid":"u","username":"listener","playlists":null,"privileges":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uuid":"u","username":"listener","playlists":null,"privileges":[]}"""),
        )

        val api = factory.requireApi()
        api.login(net.pipebomb.android.core.model.LoginDto("listener", "password"))
        api.getSelf()

        val loginRequest = server.takeRequest()
        val selfRequest = server.takeRequest()
        assertTrue(loginRequest.path!!.endsWith("/users/login"))
        assertTrue(selfRequest.getHeader("Cookie")?.contains("auth_token=test-token") == true)
    }

    @Test
    fun searchRequestBodyIncludesRequiredDefaults() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"tracks":[],"artists":[],"albums":[]}"""),
        )

        factory.requireApi().search(
            net.pipebomb.android.core.model.SearchDto(query = "hello"),
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"withTracks\":true"))
        assertTrue(body.contains("\"withAlbums\":true"))
        assertTrue(body.contains("\"withArtists\":true"))
        assertTrue(body.contains("\"query\":\"hello\""))
    }

    @Test
    fun ephemeralTrackMetadataIsAvailableToPlaybackWithoutAnotherRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "tracks": [{
                        "uuid": "ephemeral-uuid",
                        "trackId": "track",
                        "pluginId": "plugin",
                        "libraryId": "library",
                        "title": "Raw title",
                        "attributes": {
                          "title": {"type":"string","values":["Ephemeral title"],"formatted":[]},
                          "artist": {"type":"string","values":["Ephemeral artist"],"formatted":[]}
                        }
                      }],
                      "artists": [],
                      "albums": []
                    }
                    """.trimIndent(),
                ),
        )
        val repository = CatalogRepository(factory, TrackCache())

        repository.ephemeralSearch("plugin", "source", "query")
        val cached = repository.tracks(listOf(TrackKey("plugin", "library", "track")))

        val track = (cached as ApiResult.Success).value.single()
        assertEquals("Ephemeral title", AttributeReader.string(track.attributes, "title"))
        assertEquals("Ephemeral artist", AttributeReader.string(track.attributes, "artist"))
        assertEquals(1, server.requestCount)
    }
}
