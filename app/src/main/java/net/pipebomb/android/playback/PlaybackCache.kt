package net.pipebomb.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.data.storage.ResolvedStorage
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.playback.download.DownloadPinRegistry
import net.pipebomb.android.playback.download.PinnedStreamCacheEvictor
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
class TrackCacheKeyFactory(
    private val registry: MediaSessionKeyRegistry,
) : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String =
        registry.cacheKey(
            path = dataSpec.uri.encodedPath ?: dataSpec.uri.toString(),
            explicitKey = dataSpec.key,
        )

    companion object {
        fun streamKey(trackKey: String): String = "track:$trackKey:stream"
    }
}

@Singleton
@OptIn(UnstableApi::class)
class PlaybackCache @Inject constructor(
    registry: MediaSessionKeyRegistry,
    storage: StorageLocationRepository,
    private val pins: DownloadPinRegistry,
    databaseProvider: StandaloneDatabaseProvider,
) {
    val storageState: ResolvedStorage = storage.resolve()
    private val evictor = PinnedStreamCacheEvictor(pins, MAX_UNPINNED_BYTES)

    val cache: SimpleCache = SimpleCache(
        storageState.cacheDir.apply { mkdirs() },
        evictor,
        databaseProvider,
    ).also {
        evictor.attach(it)
        AppLog.info(
            category = LogCategory.CACHE,
            event = "cache.init",
            fields = mapOf(
                "fallback" to storageState.isFallback,
                "maxUnpinnedBytes" to MAX_UNPINNED_BYTES,
                "existingBytes" to it.cacheSpace,
            ),
        )
    }

    val dataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setCacheWriteDataSinkFactory(
            CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
        )
        .setUpstreamDataSourceFactory(
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true),
        )
        .setCacheKeyFactory(TrackCacheKeyFactory(registry))

    fun enablePinnedEviction() {
        evictor.enableEviction()
    }

    fun refreshPins() {
        evictor.refreshPins()
    }

    fun clear() {
        val keys = cache.keys.toList()
        val bytes = cache.cacheSpace
        keys.forEach { key -> cache.removeResource(key) }
        AppLog.info(
            category = LogCategory.CACHE,
            event = "cache.clear",
            fields = mapOf("keys" to keys.size, "bytes" to bytes),
        )
    }

    fun clearStreamed() {
        val keys = cache.keys.toList().filter { !pins.isPinned(it) }
        val bytes = cache.cacheSpace
        keys.forEach { key -> cache.removeResource(key) }
        AppLog.info(
            category = LogCategory.CACHE,
            event = "cache.clear",
            fields = mapOf("keys" to keys.size, "bytes" to bytes, "streamedOnly" to true),
        )
    }

    fun removeTrack(key: String) {
        val prefix = "track:$key:"
        val keys = cache.keys.toList().filter { it.startsWith(prefix) }
        keys.forEach { cache.removeResource(it) }
        if (keys.isNotEmpty()) {
            AppLog.debug(
                category = LogCategory.CACHE,
                event = "cache.removeTrack",
                fields = mapOf("track" to key, "keys" to keys.size),
            )
        }
    }

    fun sizeBytes(): Long = cache.cacheSpace

    companion object {
        const val MAX_UNPINNED_BYTES = 512L * 1024L * 1024L
    }
}
