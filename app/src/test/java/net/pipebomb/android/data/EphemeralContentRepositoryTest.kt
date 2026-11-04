package net.pipebomb.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.Identity
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EphemeralContentRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: CatalogRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val factory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
        )
        repository = CatalogRepository(factory, TrackCache())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun artistContentCachesTracksForPlayback() = runBlocking {
        server.enqueue(jsonResponse(artistContentJson()))

        val source = EphemeralSource(id = "source-id", pluginId = "plugin", name = "Source")
        val result = repository.artistEphemeralContent("artist-uuid", source)

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/artists/artist-uuid/ephemeral", request.path)
        assertTrue(request.body.readUtf8().contains("\"sourceId\":\"source-id\""))

        val cached = repository.tracks(listOf(TrackKey("plugin", "library", "track")))
        assertEquals(1, server.requestCount)
        assertEquals(
            "Ephemeral title",
            AttributeReader.string(
                (cached as ApiResult.Success).value.single().attributes,
                "title",
            ),
        )
    }

    @Test
    fun identityAlbumContentUsesPostIdentityEndpoint() = runBlocking {
        server.enqueue(jsonResponse(albumContentJson()))

        val result = repository.albumEphemeralContentByIdentity(
            pluginId = "plugin",
            identifierId = "identifier",
            identity = "value",
        )

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/albums/plugin/identifier/value", request.path)
    }

    @Test
    fun identitySaveUsesIdentitySaveEndpoint() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = repository.setAlbumSaved("plugin~identifier~value", saved = true)

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/albums/plugin/identifier/value/save", request.path)
    }

    @Test
    fun identityUnsaveIsLocalOnlyBecauseTheApiHasNoIdentityUnsave() = runBlocking {
        val result = repository.setArtistSaved("plugin~identifier~value", saved = false)

        assertTrue(result is ApiResult.Success)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun addAlbumToPlaylistUsesAlbumIdentity() = runBlocking {
        server.enqueue(jsonResponse("""{"uuid":"playlist"}"""))

        val result = repository.addAlbumToPlaylist(
            playlistUuid = "playlist-uuid",
            album = Album(
                identities = listOf(
                    Identity(
                        pluginId = "plugin",
                        identityId = "identifier",
                        value = "value",
                    ),
                ),
            ),
        )

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/playlists/playlist-uuid/tracks", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"albums\""))
        assertTrue(body.contains("\"pluginId\":\"plugin\""))
        assertTrue(body.contains("\"identityId\":\"identifier\""))
        assertTrue(body.contains("\"identity\":\"value\""))
        assertFalse(body.contains("\"tracks\":[{"))
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun artistContentJson(): String = """
        {
          "source": {"id":"source-id","pluginId":"plugin","name":"Source"},
          "tracks": [{
            "uuid": null,
            "trackId": "track",
            "pluginId": "plugin",
            "libraryId": "library",
            "title": "Raw title",
            "attributes": {
              "title": {"type":"string","values":["Ephemeral title"],"formatted":[]}
            }
          }],
          "albums": []
        }
    """.trimIndent()

    private fun albumContentJson(): String = """
        {
          "source": {"id":"source-id","pluginId":"plugin","name":"Source"},
          "tracks": [{
            "uuid": null,
            "trackId": "track",
            "pluginId": "plugin",
            "libraryId": "library",
            "title": "Raw title"
          }]
        }
    """.trimIndent()
}
