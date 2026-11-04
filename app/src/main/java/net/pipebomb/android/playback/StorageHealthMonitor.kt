package net.pipebomb.android.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.data.storage.StorageLocationRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageHealthMonitor @Inject constructor(
    private val storage: StorageLocationRepository,
    private val cache: PlaybackCache,
) {
    private val lastIssue = AtomicReference<String?>(null)

    suspend fun snapshot(): StorageHealth = withContext(Dispatchers.IO) {
        val health = withTimeoutOrNull(TIMEOUT_MS) { read() }
            ?: StorageHealth(
                selectedId = runCatching { storage.selectedLocationId() }.getOrDefault(""),
                label = runCatching { storage.selectedLocationLabel() }.getOrDefault("Unknown"),
                cacheDir = null,
                selectedDir = null,
                fallback = false,
                mismatch = false,
                available = false,
                freeBytes = 0,
                totalBytes = 0,
                error = "Storage check timed out. Restart Pipe Bomb if it persists.",
            )
        val issue = health.issue
        val previous = lastIssue.getAndSet(issue)
        if (issue != null && issue != previous) {
            AppLog.warn(
                category = LogCategory.STORAGE,
                event = "storage.health",
                message = issue,
                fields = mapOf(
                    "selectedId" to health.selectedId,
                    "label" to health.label,
                    "fallback" to health.fallback,
                    "mismatch" to health.mismatch,
                    "cacheDir" to health.cacheDir,
                    "selectedDir" to health.selectedDir,
                    "freeBytes" to health.freeBytes,
                ),
            )
        }
        health
    }

    private fun read(): StorageHealth {
        val resolved = storage.resolve()
        val cacheDir = runCatching { cache.storageState.cacheDir.canonicalPath }.getOrNull()
        val selectedDir = runCatching { resolved.cacheDir.canonicalPath }.getOrNull()
        val mismatch = cacheDir != null && selectedDir != null && cacheDir != selectedDir
        val fallback = resolved.isFallback
        return StorageHealth(
            selectedId = resolved.selectedId,
            label = storage.selectedLocationLabel(),
            cacheDir = cacheDir,
            selectedDir = selectedDir,
            fallback = fallback,
            mismatch = mismatch,
            available = resolved.downloadsEnabled && !fallback && !mismatch,
            freeBytes = storage.selectedAvailableBytes(),
            totalBytes = storage.selectedTotalBytes(),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 1_500L
    }
}
