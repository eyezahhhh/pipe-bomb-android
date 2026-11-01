package net.pipebomb.android.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactionTest {
    @Test
    fun urlDropsUserInfoQueryAndFragment() {
        val redacted = LogRedaction.url(
            "https://user:password@example.com:8443/api/tracks?token=secret#frag",
        )

        assertEquals("https://example.com:8443/api/tracks", redacted)
    }

    @Test
    fun urlRedactsAudioSessionPath() {
        val redacted = LogRedaction.url(
            "https://music.example.com/audio/session-abc123/hls/playlist.m3u8?token=secret",
        )

        assertEquals("https://music.example.com/audio/<redacted>", redacted)
    }

    @Test
    fun messageScrubsUrlsBearerTokensAndSecretPairs() {
        val redacted = LogRedaction.message(
            "failed https://user:pw@host:8443/a?token=query-secret " +
                "Authorization: Bearer abc123 password=hunter2",
        )

        assertTrue(redacted.contains("https://host:8443/a"))
        assertFalse(redacted.contains("user:pw"))
        assertFalse(redacted.contains("query-secret"))
        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("hunter2"))
    }

    @Test
    fun bodyScrubsSensitiveJsonValues() {
        val body = """{"message":"boom","token":"secret-token","password":"hunter2"}"""

        val redacted = LogRedaction.body(body)

        assertTrue(redacted.contains("\"message\":\"boom\""))
        assertTrue(redacted.contains("\"token\":\"***\""))
        assertTrue(redacted.contains("\"password\":\"***\""))
        assertFalse(redacted.contains("secret-token"))
        assertFalse(redacted.contains("hunter2"))
    }

    @Test
    fun bodyIsTruncated() {
        val body = "x".repeat(LogRedaction.MAX_BODY_BYTES * 2)

        val redacted = LogRedaction.body(body)

        assertTrue(redacted.endsWith("…<truncated>"))
        assertTrue(redacted.length < body.length)
    }

    @Test
    fun sessionIdIsStableShortHash() {
        val first = LogRedaction.sessionId("session-123")
        val second = LogRedaction.sessionId("session-123")

        assertEquals(first, second)
        assertEquals(8, first.length)
        assertFalse(first.contains("session-123"))
    }
}
