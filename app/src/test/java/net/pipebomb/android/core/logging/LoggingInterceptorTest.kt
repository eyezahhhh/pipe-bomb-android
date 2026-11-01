package net.pipebomb.android.core.logging

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoggingInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var recorder: RecordingAppLogger
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        recorder = RecordingAppLogger()
        AppLog.configure(recorder, DiagnosticsLevel.VERBOSE)
        client = OkHttpClient.Builder()
            .addInterceptor(LoggingInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        AppLog.configure(NoOpAppLogger, DiagnosticsLevel.Default)
    }

    @Test
    fun errorResponseIsLoggedWithoutSecretsOrQuery() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Set-Cookie", "auth_token=secret-cookie; Path=/")
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """{"message":"boom","token":"secret-token","password":"hunter2"}""",
                ),
        )

        client.newCall(
            Request.Builder()
                .url(server.url("/api/tracks?token=query-secret&page=1"))
                .build(),
        ).execute().use { response ->
            response.body.string()
        }

        val text = recorder.text()

        assertTrue(text.contains("net.request"))
        assertTrue(text.contains("net.response.error"))
        assertTrue(text.contains("\"token\":\"***\""))
        assertFalse(text.contains("secret-cookie"))
        assertFalse(text.contains("secret-token"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("query-secret"))
        assertTrue(text.contains("/api/tracks"))
    }

    @Test
    fun successfulImageRequestsAreNotLogged() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody("not-really-a-png"),
        )

        client.newCall(Request.Builder().url(server.url("/art.png")).build())
            .execute()
            .use { response ->
                response.body.string()
            }

        assertFalse(recorder.text().contains("net.request"))
        assertFalse(recorder.text().contains("/art.png"))
    }

    private class RecordingAppLogger : AppLogger {
        private val records = mutableListOf<LogRecord>()

        override fun log(record: LogRecord) {
            synchronized(records) { records += record }
        }

        override fun flush() = Unit

        override fun flushBlocking(timeoutMs: Long) = Unit

        override fun close() = Unit

        fun text(): String = synchronized(records) {
            records.joinToString("\n") { record ->
                "${record.event} ${record.message.orEmpty()} ${record.fields}"
            }
        }
    }
}
