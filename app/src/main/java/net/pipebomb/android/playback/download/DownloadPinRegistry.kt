package net.pipebomb.android.playback.download

import net.pipebomb.android.core.model.TrackKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPinRegistry @Inject constructor() {
    @Volatile
    private var prefixes: Set<String> = emptySet()

    fun setPinned(keys: Collection<TrackKey>) {
        prefixes = keys.mapTo(HashSet()) { "track:$it:" }
    }

    fun isPinned(cacheKey: String): Boolean = prefixes.any(cacheKey::startsWith)
}
