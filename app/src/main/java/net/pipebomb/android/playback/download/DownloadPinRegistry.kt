package net.pipebomb.android.playback.download

import net.pipebomb.android.core.model.TrackKey
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPinRegistry @Inject constructor() {
    private val prefixes = CopyOnWriteArraySet<String>()

    fun setPinned(keys: Collection<TrackKey>) {
        prefixes.clear()
        prefixes += keys.map { "track:$it:" }
    }

    fun isPinned(cacheKey: String): Boolean = prefixes.any(cacheKey::startsWith)
}
