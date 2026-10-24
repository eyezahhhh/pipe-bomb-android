
package net.pipebomb.android.playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ServerUrlProvider
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.core.net.rewriteServerUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedMedia(
    val trackKey: TrackKey,
    val url: String,
    val type: MediaType,
    val resolvedAt: Long,
) {
    enum class MediaType { Stream, Hls }
}

@Singleton
class PlaybackResolver @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val serverUrlProvider: ServerUrlProvider,
    private val sessionKeyRegistry: MediaSessionKeyRegistry,
) {
    private val mutex = Mutex()
    private val sessions = ConcurrentHashMap<String, ResolvedMedia>()

    suspend fun resolve(key: TrackKey, forceRefresh: Boolean = false): ApiResult<ResolvedMedia> {
        if (!forceRefresh) {
            sessions[key.toString()]?.let { return ApiResult.Success(it) }
        }
        return mutex.withLock {
            if (!forceRefresh) {
                sessions[key.toString()]?.let { return@withLock ApiResult.Success(it) }
            }
            val result = apiCall {
                apiClientFactory.requireApi().createAudioSession(
                    key.pluginId,
                    key.libraryId,
                    key.trackId,
                )
            }
            when (result) {
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
                    val resolved = ResolvedMedia(
                        trackKey = key,
                        url = url,
                        type = type,
                        resolvedAt = System.currentTimeMillis(),
                    )
                    sessions[key.toString()] = resolved
                    ApiResult.Success(resolved)
                }
            }
        }
    }

    fun invalidate(key: TrackKey) {
        sessions.remove(key.toString())
    }

    fun clear() {
        sessions.clear()
    }

    fun noServer(): ApiError = ApiError.Network("No server configured")
}
