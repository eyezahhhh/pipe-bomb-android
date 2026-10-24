package net.pipebomb.android.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlRewriterTest {
    private val server = ServerUrlParser.parse("http://10.0.2.2:3000").getOrThrow()

    @Test
    fun rewritesLoopbackResourceHostToConfiguredServer() {
        assertEquals(
            "http://10.0.2.2:3000/resources/abc/cover.jpg",
            rewriteServerUrl("http://127.0.0.1:3000/resources/abc/cover.jpg", server),
        )
    }

    @Test
    fun rewritesLocalhostStreamHostToConfiguredServer() {
        assertEquals(
            "http://10.0.2.2:3000/streaming/session/hls/playlist.m3u8",
            rewriteServerUrl(
                "http://localhost:3000/streaming/session/hls/playlist.m3u8",
                server,
            ),
        )
    }

    @Test
    fun leavesRemoteUrlsUntouched() {
        assertEquals(
            "https://cdn.example.com/cover.jpg",
            rewriteServerUrl("https://cdn.example.com/cover.jpg", server),
        )
    }

    @Test
    fun leavesLoopbackUrlWhenNoServerConfigured() {
        assertEquals(
            "http://127.0.0.1:3000/resources/abc/cover.jpg",
            rewriteServerUrl("http://127.0.0.1:3000/resources/abc/cover.jpg", null),
        )
    }
}
