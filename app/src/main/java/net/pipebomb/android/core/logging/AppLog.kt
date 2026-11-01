package net.pipebomb.android.core.logging

object AppLog {
    @Volatile
    private var logger: AppLogger = NoOpAppLogger

    @Volatile
    private var level: DiagnosticsLevel = DiagnosticsLevel.Default

    fun configure(logger: AppLogger, level: DiagnosticsLevel = this.level) {
        this.logger = logger
        this.level = level
    }

    fun setLevel(value: DiagnosticsLevel) {
        level = value
    }

    fun currentLevel(): DiagnosticsLevel = level

    fun enabled(recordLevel: LogLevel): Boolean = level.allows(recordLevel)

    fun event(
        category: LogCategory,
        event: String,
        level: LogLevel = LogLevel.INFO,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        if (!this.level.allows(level)) return
        logger.log(
            LogRecord(
                timestampMs = System.currentTimeMillis(),
                level = level,
                category = category,
                event = event,
                message = message,
                fields = fields,
            ),
        )
    }

    fun debug(
        category: LogCategory,
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = event(category, event, LogLevel.DEBUG, message, fields)

    fun info(
        category: LogCategory,
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = event(category, event, LogLevel.INFO, message, fields)

    fun warn(
        category: LogCategory,
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = event(category, event, LogLevel.WARN, message, fields)

    fun error(
        category: LogCategory,
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = event(category, event, LogLevel.ERROR, message, fields)

    fun fatal(
        category: LogCategory,
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = event(category, event, LogLevel.FATAL, message, fields)

    fun flush() = logger.flush()

    fun flushBlocking(timeoutMs: Long = 2_000) = logger.flushBlocking(timeoutMs)
}
