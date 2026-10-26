package net.pipebomb.android.playback.download

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

@OptIn(UnstableApi::class)
class PinnedStreamCacheEvictor(
    private val pins: DownloadPinRegistry,
    private val maxUnpinnedBytes: Long,
) : CacheEvictor {
    private val lock = Any()
    private val spans = TreeSet<CacheSpan>(::compare)
    private var unpinnedBytes = 0L
    private var evictionEnabled = false
    @Volatile private var cache: Cache? = null

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    fun attach(cache: Cache) {
        synchronized(lock) { this.cache = cache }
    }

    fun enableEviction() {
        synchronized(lock) { evictionEnabled = true }
        refreshPins()
    }

    fun refreshPins() {
        val candidates = synchronized(lock) {
            unpinnedBytes = spans.filterNot { pins.isPinned(it.key) }.sumOf { it.length }
            if (!evictionEnabled || unpinnedBytes <= maxUnpinnedBytes) {
                return
            }
            buildList {
                var remaining = unpinnedBytes
                for (span in spans) {
                    if (remaining <= maxUnpinnedBytes) break
                    if (!pins.isPinned(span.key)) {
                        add(span)
                        remaining -= span.length
                    }
                }
            }
        }
        val activeCache = cache ?: return
        candidates.forEach(activeCache::removeSpan)
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        synchronized(lock) {
            this.cache = cache
            if (!evictionEnabled || pins.isPinned(key) || length == C.LENGTH_UNSET.toLong()) {
                return
            }
            evictLocked(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            this.cache = cache
            spans.add(span)
            if (!pins.isPinned(span.key)) unpinnedBytes += span.length
            if (evictionEnabled) evictLocked(cache, 0)
        }
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            if (spans.remove(span) && !pins.isPinned(span.key)) {
                unpinnedBytes -= span.length
            }
        }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        synchronized(lock) {
            if (spans.remove(oldSpan) && !pins.isPinned(oldSpan.key)) {
                unpinnedBytes -= oldSpan.length
            }
            this.cache = cache
            spans.add(newSpan)
            if (!pins.isPinned(newSpan.key)) unpinnedBytes += newSpan.length
            if (evictionEnabled) evictLocked(cache, 0)
        }
    }

    private fun evictLocked(cache: Cache, requiredSpace: Long) {
        while (evictionEnabled && unpinnedBytes + requiredSpace > maxUnpinnedBytes) {
            val candidate = spans.firstOrNull { !pins.isPinned(it.key) } ?: break
            cache.removeSpan(candidate)
        }
    }

    private fun compare(lhs: CacheSpan, rhs: CacheSpan): Int {
        val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
        return if (delta == 0L) lhs.compareTo(rhs) else if (delta < 0L) -1 else 1
    }
}
