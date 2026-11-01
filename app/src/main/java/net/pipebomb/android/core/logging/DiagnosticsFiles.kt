package net.pipebomb.android.core.logging

import android.content.Context
import java.io.File

class DiagnosticsFiles(private val root: File) {
    val logsDir: File = File(root, "logs")

    fun ensureDir(): File = logsDir.apply { mkdirs() }

    fun logFiles(): List<File> =
        logsDir.listFiles { file -> file.isFile && file.extension == "log" }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

    fun totalBytes(): Long = logFiles().sumOf { it.length() }

    fun oldestTimestamp(): Long? = logFiles().minOfOrNull { it.lastModified() }

    fun clear() {
        logFiles().forEach { it.delete() }
    }

    fun prune(
        maxBytes: Long = MAX_TOTAL_BYTES,
        maxAgeMs: Long = MAX_AGE_MS,
        now: Long = System.currentTimeMillis(),
    ) {
        ensureDir()
        logFiles().filter { now - it.lastModified() > maxAgeMs }.forEach { it.delete() }
        var total = totalBytes()
        for (file in logFiles()) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    companion object {
        const val MAX_TOTAL_BYTES = 16L * 1024 * 1024
        const val MAX_FILE_BYTES = 4L * 1024 * 1024
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

        fun forContext(context: Context): DiagnosticsFiles =
            DiagnosticsFiles(File(context.filesDir, "diagnostics"))
    }
}
