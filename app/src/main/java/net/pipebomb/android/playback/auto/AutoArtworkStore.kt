package net.pipebomb.android.playback.auto

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.net.ApiClientFactory
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
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                    val bytes = body.bytes()
                    if (bytes.size > MAX_IMAGE_BYTES) return@use null
                    cache.put(url, bytes)
                    bytes
                }
            }.getOrNull()
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    }
}
