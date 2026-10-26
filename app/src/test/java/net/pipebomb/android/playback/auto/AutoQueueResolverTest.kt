package net.pipebomb.android.playback.auto

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.playback.TrackMediaUri
import net.pipebomb.android.test.TestFixtures
import okhttp3.mockwebserver.MockWebServer
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
        server.start()
        val factory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
            debugLogging = false,
        )
        resolver = AutoQueueResolver(AutoArtworkStore(factory))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun queueIsBuiltFromMetadataWithoutCreatingSessions() = runBlocking {
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
        assertEquals(
            TrackMediaUri.build(tracks[0].key),
            queue.items[0].localConfiguration?.uri,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun wholeLargeQueueIsReturnedWithoutASessionLookup() = runBlocking {
        val tracks = TestFixtures.tracks(count = 250)

        val queue = resolver.resolve(tracks, requestedIndex = 120, context = AutoContext.Playlist("p"))

        assertNotNull(queue)
        assertEquals(250, queue!!.items.size)
        assertEquals(120, queue.startIndex)
        assertEquals(0, server.requestCount)
        assertTrue(queue.items.all { it.localConfiguration?.uri?.scheme == "pipebomb" })
    }
}
