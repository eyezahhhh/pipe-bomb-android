package net.pipebomb.android.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileAppLoggerTest {
    private fun newFiles(): DiagnosticsFiles {
        val root = Files.createTempDirectory("pipebomb-log-test").toFile()
        return DiagnosticsFiles(File(root, "diagnostics"))
    }

    private fun record(index: Int): LogRecord = LogRecord(
        timestampMs = System.currentTimeMillis(),
        level = LogLevel.INFO,
        category = LogCategory.APP,
        event = "test.event",
        message = "message $index",
        fields = mapOf("index" to index),
    )

    @Test
    fun writesJsonlRecordsAndRotates() {
        val files = newFiles()
        val logger = FileAppLogger(
            files = files,
            pruneEnabled = false,
            maxFileBytes = 200,
        )

        repeat(12) { logger.log(record(it)) }
        logger.flushBlocking(2_000)

        assertTrue(files.logFiles().size >= 2)
        val lines = files.logFiles().flatMap { it.readLines() }
        assertEquals(12, lines.size)
        assertTrue(lines.all { it.contains("\"evt\":\"test.event\"") })
        assertTrue(lines.all { it.contains("\"seq\":") })
        assertTrue(lines.all { it.contains("\"sess\":") })
    }

    @Test
    fun prunesExpiredFiles() {
        val files = newFiles()
        files.ensureDir()
        val old = File(files.logsDir, "old.log")
        old.writeText("{}\n")
        old.setLastModified(System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000)
        val logger = FileAppLogger(files = files, pruneEnabled = true)

        logger.flushBlocking(2_000)

        assertFalse(old.exists())
    }

    @Test
    fun redactsSecretsBeforeWriting() {
        val files = newFiles()
        val logger = FileAppLogger(files = files, pruneEnabled = false)
        logger.log(
            LogRecord(
                timestampMs = System.currentTimeMillis(),
                level = LogLevel.WARN,
                category = LogCategory.NETWORK,
                event = "test.secret",
                message = "cookie=secret-cookie",
                fields = mapOf(
                    "url" to "https://user:pw@example.com/a?token=query-secret",
                    "authorization" to "Bearer abc123",
                ),
            ),
        )
        logger.flushBlocking(2_000)

        val content = files.logFiles().joinToString("\n") { it.readText() }

        assertFalse(content.contains("secret-cookie"))
        assertFalse(content.contains("query-secret"))
        assertFalse(content.contains("user:pw"))
        assertFalse(content.contains("abc123"))
        assertTrue(content.contains("\"evt\":\"test.secret\""))
    }
}
