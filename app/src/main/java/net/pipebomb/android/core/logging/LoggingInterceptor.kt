package net.pipebomb.android.core.logging

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val host = url.host
        val path = url.encodedPath
        val startedNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (error: IOException) {
            AppLog.warn(
                category = LogCategory.NETWORK,
                event = "net.fail",
                message = error.message,
                fields = mapOf(
                    "method" to request.method,
                    "host" to host,
                    "path" to path,
                    "exception" to error.javaClass.simpleName,
                    "durMs" to elapsedMs(startedNs),
                ),
            )
            throw error
        }
        val durationMs = elapsedMs(startedNs)
        val success = response.code in 200..299
        val contentType = response.body?.contentType()?.toString()
        val image = contentType?.startsWith("image/") == true
        val bytes = response.body?.contentLength() ?: -1L

        if (durationMs >= SLOW_THRESHOLD_MS) {
            AppLog.warn(
                category = LogCategory.NETWORK,
                event = "net.slow",
                fields = mapOf(
                    "method" to request.method,
                    "host" to host,
                    "path" to path,
                    "status" to response.code,
                    "durMs" to durationMs,
                    "bytes" to bytes,
                ),
            )
        }

        if (AppLog.enabled(LogLevel.DEBUG) && !(image && success)) {
            AppLog.debug(
                category = LogCategory.NETWORK,
                event = "net.request",
                fields = mapOf(
                    "method" to request.method,
                    "scheme" to url.scheme,
                    "host" to host,
                    "port" to url.port,
                    "path" to path,
                    "status" to response.code,
                    "durMs" to durationMs,
                    "bytes" to bytes,
                    "contentType" to contentType,
                ),
            )
        }

        if (!success && AppLog.enabled(LogLevel.DEBUG)) {
            val body = runCatching {
                response.peekBody(LogRedaction.MAX_BODY_BYTES.toLong()).string()
            }.getOrNull()
            AppLog.debug(
                category = LogCategory.NETWORK,
                event = "net.response.error",
                fields = mapOf(
                    "status" to response.code,
                    "host" to host,
                    "path" to path,
                    "contentType" to contentType,
                    "body" to body?.let(LogRedaction::body),
                ),
            )
        }

        return response
    }

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000

    private companion object {
        const val SLOW_THRESHOLD_MS = 2_000L
    }
}
