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
        val sessionId = sessionIdForPath(path)
        val trackKey = sessionId?.let(trackKeys::get)
        return if (trackKey != null) {
            "track:$trackKey:$path"
        } else {
            "uri:$path"
        }
    }

    private fun sessionIdForPath(path: String): String? {
        val segments = path.trim('/').split('/')
        val index = segments.indexOf("streaming")
        return segments.getOrNull(index + 1)?.takeIf { index >= 0 }
    }
}
