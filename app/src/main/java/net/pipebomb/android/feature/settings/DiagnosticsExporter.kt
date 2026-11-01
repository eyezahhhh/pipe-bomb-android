package net.pipebomb.android.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.pipebomb.android.BuildConfig
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.DiagnosticsFiles
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.core.logging.LogRedaction
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.SessionState
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.PlaybackController
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DiagnosticsExportResult(
    val recordCount: Int,
    val logFiles: Int,
    val bytes: Long,
)

@Singleton
class DiagnosticsExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val files: DiagnosticsFiles,
    private val settings: AppSettingsStore,
    private val storage: StorageLocationRepository,
    private val downloads: DownloadRepository,
    private val connectivity: ConnectivityStatus,
    private val auth: AuthRepository,
    private val cache: PlaybackCache,
    private val playback: PlaybackController,
) {
    private val prettyJson = Json { prettyPrint = true }

    fun totalBytes(): Long = files.totalBytes()

    fun oldestTimestamp(): Long? = files.oldestTimestamp()

    suspend fun clear() = withContext(Dispatchers.IO) {
        files.clear()
        AppLog.info(LogCategory.DIAGNOSTICS, "diagnostics.clear")
    }

    suspend fun export(uri: Uri): DiagnosticsExportResult = withContext(Dispatchers.IO) {
        AppLog.flushBlocking()
        val logFiles = files.logFiles()
        val recordCount = logFiles.sumOf { file ->
            runCatching { file.useLines { lines -> lines.count() } }.getOrDefault(0)
        }
        val bytes = logFiles.sumOf { it.length() }
        val state = buildState(logFiles, recordCount)
        val summary = buildSummary(logFiles)
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: error("The selected destination could not be opened")
        output.use { stream ->
            writeZip(
                stream = stream,
                logFiles = logFiles,
                summary = summary,
                state = state,
            )
        }
        AppLog.info(
            category = LogCategory.DIAGNOSTICS,
            event = "diagnostics.export",
            fields = mapOf(
                "records" to recordCount,
                "files" to logFiles.size,
                "bytes" to bytes,
            ),
        )
        DiagnosticsExportResult(
            recordCount = recordCount,
            logFiles = logFiles.size,
            bytes = bytes,
        )
    }

    private fun writeZip(
        stream: OutputStream,
        logFiles: List<File>,
        summary: String,
        state: String,
    ) {
        ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
            logFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("logs/${file.name}"))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("summary.txt"))
            zip.write(summary.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("state.json"))
            zip.write(state.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(README.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private suspend fun buildState(logFiles: List<File>, recordCount: Int): String {
        val serverUrl = settings.serverConfig.first().serverUrl
        val reserve = settings.downloadReserveBytes.first()
        val resolvedStorage = storage.resolve()
        val downloadRows = downloads.all()
        val session = auth.session.value
        val playbackState = playback.state.value
        val objectValue = buildJsonObject {
            put("exportedAt", System.currentTimeMillis())
            put("appVersion", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("apiContract", "0.2.0")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            put("locale", Locale.getDefault().toLanguageTag())
            put("timezone", ZoneId.systemDefault().id)
            serverUrl?.let { put("server", LogRedaction.url(it)) }
            put(
                "storage",
                buildJsonObject {
                    put("selectedId", resolvedStorage.selectedId)
                    put("label", storage.selectedLocationLabel())
                    put("fallback", resolvedStorage.isFallback)
                    put("availableBytes", storage.selectedAvailableBytes())
                    put("totalBytes", storage.selectedTotalBytes())
                    put("reserveBytes", reserve)
                },
            )
            put(
                "downloads",
                buildJsonObject {
                    put("count", downloadRows.size)
                    put("completed", downloadRows.count { it.state == "Completed" })
                    put("failed", downloadRows.count { it.state == "Failed" })
                    put("bytes", downloadRows.sumOf { it.bytesDownloaded })
                },
            )
            put("cacheBytes", cache.sizeBytes())
            put(
                "connectivity",
                buildJsonObject {
                    put("online", connectivity.isOnline.value)
                    put("metered", connectivity.isMetered.value)
                },
            )
            put(
                "session",
                buildJsonObject {
                    put("authenticated", session is SessionState.Authenticated)
                    (session as? SessionState.Authenticated)?.user?.uuid?.let { put("userId", it) }
                },
            )
            put(
                "playback",
                buildJsonObject {
                    playbackState.currentKey?.toString()?.let { put("track", it) }
                    put("queueSize", playbackState.queue.size)
                    put("index", playbackState.currentIndex)
                    put("positionMs", playbackState.positionMs)
                    put("durationMs", playbackState.durationMs)
                    put("isPlaying", playbackState.isPlaying)
                    put("isBuffering", playbackState.isBuffering)
                    playbackState.error?.let { put("error", it) }
                },
            )
            put(
                "logs",
                buildJsonObject {
                    put("files", logFiles.size)
                    put("bytes", logFiles.sumOf { it.length() })
                    put("records", recordCount)
                    files.oldestTimestamp()?.let { put("oldestMs", it) }
                },
            )
        }
        return prettyJson.encodeToString(JsonObject.serializer(), objectValue)
    }

    private fun buildSummary(logFiles: List<File>): String {
        val lines = logFiles.flatMap { file ->
            runCatching { file.useLines { it.toList() } }.getOrDefault(emptyList())
        }
        val interesting = lines.mapNotNull { line ->
            val record = runCatching {
                Json.parseToJsonElement(line).jsonObject
            }.getOrNull() ?: return@mapNotNull null
            val level = record["lvl"]?.jsonPrimitive?.contentOrNull
            if (level !in SUMMARY_LEVELS) return@mapNotNull null
            formatSummaryLine(record)
        }.takeLast(SUMMARY_LIMIT)
        return buildString {
            appendLine("Pipe Bomb Android diagnostics summary")
            appendLine("Newest ${interesting.size} warnings/errors. Full JSONL in logs/.")
            appendLine()
            interesting.forEach(::appendLine)
        }
    }

    private fun formatSummaryLine(record: JsonObject): String {
        val iso = record["iso"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val level = record["lvl"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val category = record["cat"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val event = record["evt"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val message = record["msg"]?.jsonPrimitive?.contentOrNull
        val extras = SUMMARY_FIELDS.mapNotNull { key ->
            record[key]?.let { value ->
                runCatching { "$key=${value.jsonPrimitive.content}" }.getOrNull()
            }
        }.joinToString(" ")
        return buildString {
            append(iso)
            append(' ')
            append(level)
            append(' ')
            append(category)
            append(' ')
            append(event)
            if (!message.isNullOrBlank()) {
                append(" — ")
                append(message)
            }
            if (extras.isNotBlank()) {
                append(" [")
                append(extras)
                append(']')
            }
        }.take(MAX_SUMMARY_LINE)
    }

    private companion object {
        const val SUMMARY_LIMIT = 200
        const val MAX_SUMMARY_LINE = 400
        val SUMMARY_LEVELS = setOf("W", "E", "F")
        val SUMMARY_FIELDS = listOf(
            "track",
            "status",
            "kind",
            "code",
            "reason",
            "path",
            "durMs",
            "bytes",
        )

        const val README = """Pipe Bomb Android diagnostics
=============================

logs/*.log   JSONL, one record per line, oldest first.
summary.txt  The newest 200 warnings/errors in plain text.
state.json   App, device and current-state snapshot taken at export time.

Redaction: cookies, authorization headers, request bodies, URL queries and
URL userinfo are never logged. Non-2xx response bodies are truncated to 1 KB
and JSON values whose keys look sensitive are replaced with "***".
Timestamps are epoch milliseconds plus an ISO-8601 local-offset string.
"""
    }
}
