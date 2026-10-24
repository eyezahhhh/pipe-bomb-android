package net.pipebomb.android.core.net

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentCookieJarTest {
    private val url = "https://music.example.com/".toHttpUrl()

    @Test
    fun storesAndReturnsCookie() {
        val storage = InMemoryCookieStorage()
        val jar = PersistentCookieJar(storage, now = { 1000L })
        val cookie = Cookie.parse(url, "auth_token=abc; Path=/; Max-Age=3600; HttpOnly")!!
        jar.saveFromResponse(url, listOf(cookie))

        val loaded = jar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("auth_token", loaded.first().name)
        assertEquals("abc", loaded.first().value)
        assertTrue(loaded.first().httpOnly)
        assertEquals(1, storage.load().size)
    }

    @Test
    fun doesNotSendCookieToDifferentHost() {
        val storage = InMemoryCookieStorage()
        val jar = PersistentCookieJar(storage, now = { 1000L })
        jar.saveFromResponse(url, listOf(Cookie.parse(url, "auth_token=abc; Path=/")!!))

        assertTrue(jar.loadForRequest("https://other.example.com/".toHttpUrl()).isEmpty())
    }

    @Test
    fun expiredCookieIsPruned() {
        val storage = InMemoryCookieStorage()
        var now = 1000L
        val jar = PersistentCookieJar(storage, now = { now })
        val expiring = Cookie.Builder()
            .name("auth_token")
            .value("abc")
            .domain("music.example.com")
            .path("/")
            .expiresAt(2000L)
            .build()
        jar.saveFromResponse(url, listOf(expiring))
        now = 10_000L

        assertTrue(jar.loadForRequest(url).isEmpty())
        assertTrue(storage.load().isEmpty())
    }

    @Test
    fun clearRemovesAllCookies() {
        val storage = InMemoryCookieStorage()
        val jar = PersistentCookieJar(storage)
        jar.saveFromResponse(url, listOf(Cookie.parse(url, "auth_token=abc; Path=/")!!))
        jar.clear()

        assertTrue(jar.loadForRequest(url).isEmpty())
    }
}
