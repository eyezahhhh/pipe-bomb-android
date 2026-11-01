package net.pipebomb.android.core.logging

data class LogRecord(
    val timestampMs: Long,
    val level: LogLevel,
    val category: LogCategory,
    val event: String,
    val message: String?,
    val fields: Map<String, Any?>,
)
