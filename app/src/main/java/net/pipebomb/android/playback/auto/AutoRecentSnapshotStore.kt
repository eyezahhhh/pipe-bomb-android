package net.pipebomb.android.playback.auto

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import net.pipebomb.android.core.model.Track

@Singleton
class AutoRecentSnapshotStore @Inject constructor() {
    private val lock = Any()
    private val snapshots = LinkedHashMap<String, Snapshot>()
    private var currentId: String? = null

    fun fresh(now: Long = System.currentTimeMillis()): Snapshot? = synchronized(lock) {
        currentId?.let { snapshots[it] }?.takeIf { now - it.createdAt < TTL_MILLIS }
    }

    fun put(tracks: List<Track>, now: Long = System.currentTimeMillis()): Snapshot =
        synchronized(lock) {
            prune(now)
            Snapshot(UUID.randomUUID().toString(), tracks, now).also {
                snapshots[it.id] = it
                currentId = it.id
            }
        }

    fun get(snapshotId: String, now: Long = System.currentTimeMillis()): Snapshot? =
        synchronized(lock) {
            snapshots[snapshotId]?.takeIf { now - it.createdAt < TTL_MILLIS }
        }

    private fun prune(now: Long) {
        snapshots.entries.removeAll { now - it.value.createdAt >= TTL_MILLIS }
        while (snapshots.size >= MAX_SNAPSHOTS) {
            val oldest = snapshots.entries.minByOrNull { it.value.createdAt } ?: break
            snapshots.remove(oldest.key)
        }
    }

    data class Snapshot(
        val id: String,
        val tracks: List<Track>,
        val createdAt: Long,
    )

    private companion object {
        const val TTL_MILLIS = 10 * 60_000L
        const val MAX_SNAPSHOTS = 4
    }
}
