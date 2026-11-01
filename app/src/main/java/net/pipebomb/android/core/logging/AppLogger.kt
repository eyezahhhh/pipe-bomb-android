package net.pipebomb.android.core.logging

interface AppLogger {
    fun log(record: LogRecord)

    fun flush()

    fun flushBlocking(timeoutMs: Long = 2_000)

    fun close()
}

object NoOpAppLogger : AppLogger {
    override fun log(record: LogRecord) = Unit

    override fun flush() = Unit

    override fun flushBlocking(timeoutMs: Long) = Unit

    override fun close() = Unit
}
