package net.pipebomb.android.core.logging

import androidx.annotation.OptIn
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class Media3LogSink : Media3Log.Logger {
    override fun d(tag: String, message: String, throwable: Throwable?) {
        emit(LogLevel.DEBUG, tag, message, throwable)
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        emit(LogLevel.INFO, tag, message, throwable)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        emit(LogLevel.WARN, tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        emit(LogLevel.ERROR, tag, message, throwable)
    }

    private fun emit(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (!AppLog.enabled(level)) return
        AppLog.event(
            category = LogCategory.MEDIA3,
            event = "media3.$tag",
            level = level,
            message = message,
            fields = if (throwable == null) {
                emptyMap()
            } else {
                mapOf("throwable" to LogRedaction.throwable(throwable))
            },
        )
    }
}
