package net.pipebomb.android.core.logging

class CrashLogHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            AppLog.fatal(
                category = LogCategory.CRASH,
                event = "app.crash.uncaught",
                message = throwable.toString(),
                fields = mapOf(
                    "thread" to thread.name,
                    "stack" to LogRedaction.throwable(throwable, limit = 32_768),
                ),
            )
            AppLog.flushBlocking()
        }
        previous?.uncaughtException(thread, throwable)
    }
}
