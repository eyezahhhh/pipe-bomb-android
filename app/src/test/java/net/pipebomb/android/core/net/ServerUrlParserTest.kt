package net.pipebomb.android.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlParserTest {
    @Test
    fun publicHostWithoutSchemeDefaultsToHttps() {
        val url = ServerUrlParser.parse("music.example.com").getOrThrow()
        assertEquals("https", url.scheme)
        assertEquals("music.example.com", url.host)
        assertEquals("/", url.encodedPath)
    }

    @Test
    fun localAddressWithoutSchemeDefaultsToHttp() {
        val url = ServerUrlParser.parse("192.168.1.20:3000").getOrThrow()
        assertEquals("http", url.scheme)
        assertEquals(3000, url.port)
        assertEquals("/", url.encodedPath)
    }

    @Test
    fun localhostDefaultsToHttp() {
        val url = ServerUrlParser.parse("localhost:3000").getOrThrow()
        assertEquals("http", url.scheme)
        assertEquals("localhost", url.host)
    }

    @Test
    fun trailingSlashIsAdded() {
        val url = ServerUrlParser.parse("https://music.example.com/api").getOrThrow()
        assertEquals("/api/", url.encodedPath)
    }

    @Test
    fun queryAndFragmentAreDropped() {
        val url = ServerUrlParser.parse("https://music.example.com/api?token=secret#frag").getOrThrow()
        assertEquals("/api/", url.encodedPath)
        assertEquals(null, url.query)
        assertEquals(null, url.fragment)
    }

    @Test
    fun blankInputFails() {
        assertTrue(ServerUrlParser.parse("   ").isFailure)
    }

    @Test
    fun invalidInputFails() {
        assertTrue(ServerUrlParser.parse("not a url").isFailure)
    }
}
