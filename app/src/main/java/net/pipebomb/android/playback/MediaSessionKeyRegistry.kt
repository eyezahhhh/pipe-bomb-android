package net.pipebomb.android.playback

import net.pipebomb.android.core.model.TrackKey
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSessionKeyRegistry @Inject constructor() {
    private val trackKeys = ConcurrentHashMap<String, String>()

    fun register(sessionId: String, trackKey: TrackKey) {
        trackKeys[sessionId] = trackKey.toString()
    }

    fun cacheKey(path: String, explicitKey: String? = null): String {
        explicitKey?.let { return it }
        val segments = path.trim('/').split('/')
        val streamingIndex = segments.indexOf("streaming")
        val sessionId = segments.getOrNull(streamingIndex + 1)
        if (streamingIndex < 0 || sessionId == null) return "uri:$path"
        val trackKey = trackKeys[sessionId] ?: return "uri:$path"
        val relativePath = segments.drop(streamingIndex + 2).joinToString("/")
        return when {
            relativePath.isBlank() -> "track:$trackKey"
            relativePath.startsWith("hls/") ->
                "track:$trackKey:hls:${relativePath.removePrefix("hls/")}"

            else -> "track:$trackKey:$relativePath"
        }
    }
}
