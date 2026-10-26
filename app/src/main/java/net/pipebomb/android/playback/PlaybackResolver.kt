package net.pipebomb.android.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ServerUrlProvider
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.core.net.rewriteServerUrl
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.storage.StorageLocationRepository
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
    private val storage: StorageLocationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val sessions = ConcurrentHashMap<String, ResolvedMedia>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ApiResult<ResolvedMedia>>>()

    suspend fun resolve(key: TrackKey, forceRefresh: Boolean = false): ApiResult<ResolvedMedia> {
        downloadRepository.completed(key)?.let { download ->
            if (!storage.resolve().downloadsEnabled) {
                return ApiResult.Failure(
                    ApiError.Local("Downloaded audio is on unavailable storage. Reconnect it or change the storage location."),
                )
            }
            sessionKeyRegistry.register(download.sessionId, key)
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

        val id = key.toString()
        if (!forceRefresh) {
            sessions[id]?.takeIf { isFresh(it) }?.let { return ApiResult.Success(it) }
        } else {
            sessions.remove(id)
        }
        val request = inFlight.computeIfAbsent(id) {
            scope.async { createSession(key) }
        }
        return try {
            val result = request.await()
            if (result is ApiResult.Success) sessions[id] = result.value
            result
        } finally {
            inFlight.remove(id, request)
        }
    }

    fun invalidate(key: TrackKey) {
        sessions.remove(key.toString())
    }

    fun clear() {
        sessions.clear()
    }

    fun noServer(): ApiError = ApiError.Network("No server configured")

    private suspend fun createSession(key: TrackKey): ApiResult<ResolvedMedia> {
        val result = apiCall {
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
                    ),
                )
            }
        }
    }

    private fun isFresh(media: ResolvedMedia): Boolean =
        System.currentTimeMillis() - media.resolvedAt < SESSION_TTL_MS

    private companion object {
        const val SESSION_TTL_MS = 10 * 60 * 1000L
    }
}
