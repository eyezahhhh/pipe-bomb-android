package net.pipebomb.android.core.logging

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class FileAppLogger(
    private val files: DiagnosticsFiles,
    private val pruneEnabled: Boolean = true,
    private val maxFileBytes: Long = DiagnosticsFiles.MAX_FILE_BYTES,
    private val maxTotalBytes: Long = DiagnosticsFiles.MAX_TOTAL_BYTES,
    private val maxAgeMs: Long = DiagnosticsFiles.MAX_AGE_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : AppLogger {
    private val sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
    private val sessionStartedMs = clock()
    private val filePrefix = "pipebomb-${
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
            .format(Instant.ofEpochMilli(sessionStartedMs).atZone(ZoneId.systemDefault()))
    }-$sessionId"
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val channel = Channel<LogRecord>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val dropped = AtomicLong(0)
    private val lock = Any()
    private val json = Json

    private var sequence = 0L
    private var fileIndex = 0
    private var currentFile: File? = null
    private var currentWriter: BufferedWriter? = null
    private var currentStream: FileOutputStream? = null
    private var lastSyncMs = 0L
    private var lastPruneMs = 0L
    private var prunedOnce = false

    @Volatile
    private var closed = false

    init {
        scope.launch {
            try {
                for (record in channel) {
                    synchronized(lock) {
                        runCatching { writeLocked(record) }
                    }
                }
            } finally {
                synchronized(lock) { runCatching { closeLocked() } }
            }
        }
    }

    override fun log(record: LogRecord) {
        if (closed) return
        if (record.level == LogLevel.FATAL) {
            synchronized(lock) { runCatching { writeLocked(record) } }
            return
        }
        if (channel.trySend(record).isFailure) {
            dropped.incrementAndGet()
        }
    }

    override fun flush() {
        if (closed) return
        scope.launch {
            synchronized(lock) {
                currentWriter?.flush()
                syncLocked()
            }
        }
    }

    override fun flushBlocking(timeoutMs: Long) {
        val deadline = clock() + timeoutMs
        runCatching {
            while (clock() < deadline) {
                val record = channel.tryReceive().getOrNull() ?: break
                synchronized(lock) { writeLocked(record) }
            }
            synchronized(lock) {
                currentWriter?.flush()
                syncLocked()
                if (pruneEnabled) pruneLocked(force = true)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        channel.close()
    }

    private fun writeLocked(record: LogRecord) {
        ensureOpenLocked()
        if (dropped.get() > 0) {
            val count = dropped.getAndSet(0)
            if (count > 0) {
                writeLineLocked(
                    LogRecord(
                        timestampMs = clock(),
                        level = LogLevel.WARN,
                        category = LogCategory.DIAGNOSTICS,
                        event = "log.dropped",
                        message = null,
                        fields = mapOf("count" to count),
                    ),
                )
            }
        }
        writeLineLocked(record)
        currentWriter?.flush()
        val now = clock()
        if (record.level.priority >= LogLevel.ERROR.priority || now - lastSyncMs >= SYNC_INTERVAL_MS) {
            syncLocked()
            lastSyncMs = now
        }
        if ((currentFile?.length() ?: 0L) >= maxFileBytes) {
            rotateLocked()
        }
        if (pruneEnabled && now - lastPruneMs >= PRUNE_INTERVAL_MS) {
            pruneLocked(force = false)
        }
    }

    private fun writeLineLocked(record: LogRecord) {
        val timestamp = clock().let { if (record.timestampMs > 0) record.timestampMs else it }
        val objectValue = buildJsonObject {
            put("ts", timestamp)
            put(
                "iso",
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
                ),
            )
            put("seq", sequence++)
            put("sess", sessionId)
            put("lvl", record.level.token)
            put("cat", record.category.token)
            put("evt", record.event)
            record.message?.let { put("msg", LogRedaction.message(it)) }
            record.fields.forEach { (key, value) ->
                put(key.toString(), value.toJsonElement())
            }
        }
        val line = json.encodeToString(JsonObject.serializer(), objectValue)
        currentWriter?.append(line)
        currentWriter?.append('\n')
    }

    private fun ensureOpenLocked() {
        if (currentWriter != null) return
        files.ensureDir()
        if (pruneEnabled && !prunedOnce) {
            pruneLocked(force = true)
        }
        openNextFileLocked()
    }

    private fun openNextFileLocked() {
        closeFileLocked()
        fileIndex += 1
        val file = File(files.logsDir, "$filePrefix-$fileIndex.log")
        currentFile = file
        currentStream = FileOutputStream(file, true)
        currentWriter = BufferedWriter(OutputStreamWriter(currentStream, Charsets.UTF_8))
    }

    private fun rotateLocked() {
        openNextFileLocked()
        if (pruneEnabled) pruneLocked(force = false)
    }

    private fun syncLocked() {
        runCatching { currentStream?.fd?.sync() }
    }

    private fun pruneLocked(force: Boolean) {
        val now = clock()
        if (!force && now - lastPruneMs < PRUNE_INTERVAL_MS) return
        lastPruneMs = now
        prunedOnce = true
        val active = currentFile
        var total = files.totalBytes()
        files.logFiles()
            .filter { it != active && now - it.lastModified() > maxAgeMs }
            .forEach { file ->
                val length = file.length()
                if (file.delete()) total -= length
            }
        for (file in files.logFiles()) {
            if (total <= maxTotalBytes) break
            if (file == active) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun closeFileLocked() {
        runCatching { currentWriter?.flush() }
        runCatching { currentWriter?.close() }
        runCatching { currentStream?.close() }
        currentWriter = null
        currentStream = null
        currentFile = null
    }

    private fun closeLocked() {
        runCatching { currentWriter?.flush() }
        syncLocked()
        closeFileLocked()
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(LogRedaction.message(this))
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Throwable -> JsonPrimitive(LogRedaction.throwable(this))
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        is Map<*, *> -> JsonObject(
            entries.associate { (key, value) -> key.toString() to value.toJsonElement() },
        )
        else -> JsonPrimitive(LogRedaction.message(toString()))
    }

    private companion object {
        const val CHANNEL_CAPACITY = 4096
        const val SYNC_INTERVAL_MS = 5_000L
        const val PRUNE_INTERVAL_MS = 5 * 60_000L
    }
}
