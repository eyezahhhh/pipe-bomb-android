package net.pipebomb.android.data

import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackCache @Inject constructor() {
    private data class Entry(val track: Track, val fetchedAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: TrackKey, now: Long = System.currentTimeMillis()): Track? {
        val entry = entries[key.toString()] ?: return null
        if (now - entry.fetchedAt > TTL_MILLIS) {
            entries.remove(key.toString())
            return null
        }
        return entry.track
    }

    fun putAll(tracks: List<Track>, now: Long = System.currentTimeMillis()) {
        for (track in tracks) {
            entries[track.key.toString()] = Entry(track, now)
        }
        if (entries.size > MAX_ENTRIES) {
            entries.entries
                .sortedBy { it.value.fetchedAt }
                .take(entries.size - MAX_ENTRIES)
                .forEach { entries.remove(it.key) }
        }
    }

    fun clear() {
        entries.clear()
    }

    private companion object {
        const val TTL_MILLIS = 10 * 60 * 1000L
        const val MAX_ENTRIES = 2000
    }
}
