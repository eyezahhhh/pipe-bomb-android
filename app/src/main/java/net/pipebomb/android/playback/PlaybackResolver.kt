package net.pipebomb.android.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.core.logging.LogRedaction
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ServerUrlProvider
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.core.net.kindLabel
import net.pipebomb.android.core.net.rewriteServerUrl
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedMedia(
    val trackKey: TrackKey,
    val url: String,
    val type: MediaType,
    val resolvedAt: Long,
    val sessionId: String,
    val fromDownload: Boolean = false,
    val bypassCache: Boolean = false,
    val downloadTitle: String? = null,
    val downloadArtist: String? = null,
    val downloadArtworkUrl: String? = null,
) {
    enum class MediaType { Stream, Hls }
}

@Singleton
class PlaybackResolver @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val serverUrlProvider: ServerUrlProvider,
    private val sessionKeyRegistry: MediaSessionKeyRegistry,
    private val downloadRepository: DownloadRepository,
    private val cache: PlaybackCache,
    private val connectivity: ConnectivityStatus,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val sessions = ConcurrentHashMap<String, ResolvedMedia>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ApiResult<ResolvedMedia>>>()

    suspend fun resolve(key: TrackKey, forceRefresh: Boolean = false): ApiResult<ResolvedMedia> {
        val startedNs = System.nanoTime()
        val cacheHealthy = withContext(Dispatchers.IO) {
            runCatching { cache.isDownloadStorageHealthy() }.getOrDefault(false)
        }
        val download = downloadRepository.completed(key)
        if (download != null && cacheHealthy) {
            sessionKeyRegistry.register(download.sessionId, key)
            AppLog.info(
                category = LogCategory.PLAYBACK,
                event = "playback.resolve",
                fields = mapOf(
                    "track" to key.toString(),
                    "source" to "download",
                    "mediaType" to "stream",
                    "sessionHash" to LogRedaction.sessionId(download.sessionId),
                    "durMs" to elapsedMs(startedNs),
                ),
            )
            return ApiResult.Success(
                ResolvedMedia(
                    trackKey = key,
                    url = download.mediaUrl,
                    type = ResolvedMedia.MediaType.Stream,
                    resolvedAt = download.completedAt ?: System.currentTimeMillis(),
                    sessionId = download.sessionId,
                    fromDownload = true,
                    downloadTitle = download.title,
                    downloadArtist = download.artist,
                    downloadArtworkUrl = download.artworkUrl,
                ),
            )
        }
        if (download != null) {
            AppLog.warn(
                category = LogCategory.PLAYBACK,
                event = "playback.resolve.download_unavailable",
                fields = mapOf(
                    "track" to key.toString(),
                    "online" to connectivity.isOnline.value,
                ),
            )
            if (!connectivity.isOnline.value) {
                return ApiResult.Failure(
                    ApiError.Local(
                        "Downloaded audio is on unavailable storage. Reconnect the storage and restart Pipe Bomb.",
                    ),
                )
            }
        }

        val id = key.toString()
        if (!forceRefresh && cacheHealthy) {
            sessions[id]?.takeIf { isFresh(it) }?.let { cached ->
                AppLog.debug(
                    category = LogCategory.PLAYBACK,
                    event = "playback.resolve",
                    fields = mapOf(
                        "track" to key.toString(),
                        "source" to "session",
                        "mediaType" to cached.type.name.lowercase(),
                        "sessionHash" to LogRedaction.sessionId(cached.sessionId),
                        "cacheHit" to true,
                        "bypassCache" to cached.bypassCache,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
                return ApiResult.Success(cached)
            }
        } else {
            sessions.remove(id)
        }
        val requestKey = id
        val request = inFlight.computeIfAbsent(requestKey) {
            scope.async { createSession(key, bypassCache = false) }
        }
        return try {
            val result = request.await()
            if (result is ApiResult.Success) {
                sessions[id] = result.value
                AppLog.info(
                    category = LogCategory.PLAYBACK,
                    event = "playback.resolve",
                    fields = mapOf(
                        "track" to key.toString(),
                        "source" to "session",
                        "mediaType" to result.value.type.name.lowercase(),
                        "sessionHash" to LogRedaction.sessionId(result.value.sessionId),
                        "cacheHit" to false,
                        "bypassCache" to result.value.bypassCache,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
            } else if (result is ApiResult.Failure) {
                AppLog.warn(
                    category = LogCategory.PLAYBACK,
                    event = "playback.resolve",
                    message = result.error.message,
                    fields = mapOf(
                        "track" to key.toString(),
                        "kind" to result.error.kindLabel(),
                        "forceRefresh" to forceRefresh,
                        "bypassCache" to !cacheHealthy,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
            }
            result
        } finally {
            inFlight.remove(requestKey, request)
        }
    }

    fun invalidate(key: TrackKey) {
        sessions.remove(key.toString())
        AppLog.debug(
            category = LogCategory.PLAYBACK,
            event = "playback.resolve.invalidate",
            fields = mapOf("track" to key.toString()),
        )
    }

    fun clear() {
        val count = sessions.size
        sessions.clear()
        AppLog.info(
            category = LogCategory.PLAYBACK,
            event = "playback.resolve.clear",
            fields = mapOf("count" to count),
        )
    }

    fun noServer(): ApiError = ApiError.Network("No server configured")

    private suspend fun createSession(
        key: TrackKey,
        bypassCache: Boolean,
    ): ApiResult<ResolvedMedia> {
        val result = apiCall("audio.session") {
            apiClientFactory.requireApi().createAudioSession(
                key.pluginId,
                key.libraryId,
                key.trackId,
            )
        }
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val session = result.value
                sessionKeyRegistry.register(session.id, key)
                val type = if (session.type == "hls") {
                    ResolvedMedia.MediaType.Hls
                } else {
                    ResolvedMedia.MediaType.Stream
                }
                val base = rewriteServerUrl(session.baseUrl, serverUrlProvider.current()).trimEnd('/')
                val url = when (type) {
                    ResolvedMedia.MediaType.Hls -> "$base/hls/playlist.m3u8"
                    ResolvedMedia.MediaType.Stream -> "$base/stream"
                }
                ApiResult.Success(
                    ResolvedMedia(
                        trackKey = key,
                        url = url,
                        type = type,
                        resolvedAt = System.currentTimeMillis(),
                        sessionId = session.id,
                        bypassCache = bypassCache,
                    ),
                )
            }
        }
    }

    private fun isFresh(media: ResolvedMedia): Boolean =
        System.currentTimeMillis() - media.resolvedAt < SESSION_TTL_MS

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000

    private companion object {
        const val SESSION_TTL_MS = 10 * 60 * 1000L
    }
}
