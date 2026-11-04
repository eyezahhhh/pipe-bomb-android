package net.pipebomb.android.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.data.storage.ResolvedStorage
import net.pipebomb.android.data.storage.StorageLocationRepository
import java.io.File
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

/**
 * Two independent caches:
 *
 * - [streamCache] is always on internal storage and is bounded by an LRU budget.
 * - [downloadCache] lives on the user-selected volume and is never evicted; downloads are
 *   user-managed and space-gated.
 *
 * The existing `exoplayer_internal.db` and selected-storage directory are kept for the
 * download cache, so downloads survive this split without a migration. The streamed cache
 * gets a new internal directory and a separate database index.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlaybackCache @Inject constructor(
    @param:ApplicationContext context: Context,
    registry: MediaSessionKeyRegistry,
    private val storage: StorageLocationRepository,
    databaseProvider: StandaloneDatabaseProvider,
) {
    val storageState: ResolvedStorage by lazy { storage.resolve() }
    private val cacheKeyFactory = TrackCacheKeyFactory(registry)

    val downloadCache: SimpleCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimpleCache(
            storageState.cacheDir.apply { mkdirs() },
            NoOpCacheEvictor(),
            databaseProvider,
        ).also {
            AppLog.info(
                category = LogCategory.CACHE,
                event = "cache.init",
                fields = mapOf(
                    "kind" to "download",
                    "fallback" to storageState.isFallback,
                    "dir" to storageState.cacheDir.absolutePath,
                    "existingBytes" to it.cacheSpace,
                ),
            )
        }
    }

    val streamCache: SimpleCache = SimpleCache(
        File(context.filesDir, STREAM_DIR).apply { mkdirs() },
        LeastRecentlyUsedCacheEvictor(MAX_STREAM_BYTES),
        NamedDatabaseProvider(context, STREAM_DB_NAME),
    ).also {
        AppLog.info(
            category = LogCategory.CACHE,
            event = "cache.init",
            fields = mapOf(
                "kind" to "stream",
                "maxBytes" to MAX_STREAM_BYTES,
                "existingBytes" to it.cacheSpace,
            ),
        )
    }

    private val downloadFactory: CacheDataSource.Factory by lazy {
        cacheDataSourceFactory(downloadCache)
    }

    /**
     * Defers opening the selected-storage cache until a download is actually read. Building a
     * [DefaultMediaSourceFactory] with this factory does not touch the SD card.
     */
    val downloadDataSourceFactory: DataSource.Factory = DataSource.Factory {
        downloadFactory.createDataSource()
    }

    val streamDataSourceFactory: CacheDataSource.Factory =
        cacheDataSourceFactory(streamCache)

    val bypassDataSourceFactory: DataSource.Factory = upstreamFactory()

    fun isDownloadStorageHealthy(): Boolean {
        val resolved = storage.resolve()
        if (!resolved.downloadsEnabled || resolved.isFallback) return false
        return runCatching {
            resolved.cacheDir.canonicalPath == storageState.cacheDir.canonicalPath
        }.getOrDefault(false)
    }

    fun clear() {
        clearCache(downloadCache, "download")
        clearCache(streamCache, "stream")
    }

    fun clearStreamed() {
        clearCache(streamCache, "stream")
    }

    fun clearDownloads() {
        clearCache(downloadCache, "download")
    }

    fun removeTrack(key: String) {
        val prefix = "track:$key:"
        val keys = downloadCache.keys.toList().filter { it.startsWith(prefix) }
        keys.forEach { downloadCache.removeResource(it) }
        if (keys.isNotEmpty()) {
            AppLog.debug(
                category = LogCategory.CACHE,
                event = "cache.removeTrack",
                fields = mapOf("track" to key, "keys" to keys.size, "kind" to "download"),
            )
        }
    }

    /**
     * One-time cleanup for installs that previously kept streamed spans in the download cache.
     * Keys that do not belong to any current download row are stale streamed data.
     */
    fun removeUnreferencedDownloadSpans(downloadedTrackKeys: Collection<String>) {
        val prefixes = downloadedTrackKeys.map { "track:$it:" }
        val stale = downloadCache.keys.toList().filterNot { key ->
            prefixes.any(key::startsWith)
        }
        stale.forEach { downloadCache.removeResource(it) }
        if (stale.isNotEmpty()) {
            AppLog.info(
                category = LogCategory.CACHE,
                event = "cache.cleanup",
                fields = mapOf("keys" to stale.size, "kind" to "download"),
            )
        }
    }

    fun streamBytes(): Long = streamCache.cacheSpace

    fun downloadBytes(): Long = downloadCache.cacheSpace

    fun sizeBytes(): Long = streamBytes() + downloadBytes()

    private fun cacheDataSourceFactory(cache: SimpleCache): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
            )
            .setUpstreamDataSourceFactory(upstreamFactory())
            .setCacheKeyFactory(cacheKeyFactory)

    private fun upstreamFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

    private fun clearCache(cache: SimpleCache, kind: String) {
        val keys = cache.keys.toList()
        val bytes = cache.cacheSpace
        keys.forEach { key -> cache.removeResource(key) }
        AppLog.info(
            category = LogCategory.CACHE,
            event = "cache.clear",
            fields = mapOf("keys" to keys.size, "bytes" to bytes, "kind" to kind),
        )
    }

    companion object {
        const val MAX_STREAM_BYTES = 512L * 1024L * 1024L
        private const val STREAM_DIR = "media-stream"
        private const val STREAM_DB_NAME = "exoplayer_stream.db"
    }
}
