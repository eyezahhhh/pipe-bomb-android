package net.pipebomb.android.playback.auto

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.net.ApiClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoArtworkStore @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
) {
    private val client: OkHttpClient = apiClientFactory.client.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, ByteArray>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun load(resource: Resource?): ByteArray? {
        val url = resource?.url?.takeIf { it.isNotBlank() } ?: return null
        cache.get(url)?.let { return it }
        val host = url.toHttpUrlOrNull()?.host
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.warn(
                            category = LogCategory.AUTO,
                            event = "auto.artwork.failed",
                            message = "HTTP ${response.code}",
                            fields = mapOf(
                                "host" to host,
                                "status" to response.code,
                            ),
                        )
                        return@use null
                    }
                    val body = response.body
                    val contentLength = body.contentLength()
                    if (contentLength > MAX_IMAGE_BYTES) {
                        AppLog.warn(
                            category = LogCategory.AUTO,
                            event = "auto.artwork.failed",
                            message = "image too large",
                            fields = mapOf(
                                "host" to host,
                                "contentLength" to contentLength,
                            ),
                        )
                        return@use null
                    }
                    val bytes = body.bytes()
                    if (bytes.size > MAX_IMAGE_BYTES) {
                        AppLog.warn(
                            category = LogCategory.AUTO,
                            event = "auto.artwork.failed",
                            message = "image too large",
                            fields = mapOf(
                                "host" to host,
                                "bytes" to bytes.size,
                            ),
                        )
                        return@use null
                    }
                    cache.put(url, bytes)
                    AppLog.debug(
                        category = LogCategory.AUTO,
                        event = "auto.artwork",
                        fields = mapOf(
                            "host" to host,
                            "bytes" to bytes.size,
                        ),
                    )
                    bytes
                }
            }.onFailure { error ->
                AppLog.warn(
                    category = LogCategory.AUTO,
                    event = "auto.artwork.failed",
                    message = error.message,
                    fields = mapOf(
                        "host" to host,
                        "exception" to error.javaClass.simpleName,
                    ),
                )
            }.getOrNull()
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    }
}
