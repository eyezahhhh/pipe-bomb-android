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
    ).also { evictor.attach(it) }

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
        cache.keys.toList().forEach { key ->
            cache.removeResource(key)
        }
    }

    fun clearStreamed() {
        cache.keys.toList().forEach { key ->
            if (!pins.isPinned(key)) cache.removeResource(key)
        }
    }

    fun removeTrack(key: String) {
        val prefix = "track:$key:"
        cache.keys.toList()
            .filter { it.startsWith(prefix) }
            .forEach { cache.removeResource(it) }
    }

    fun sizeBytes(): Long = cache.cacheSpace

    companion object {
        const val MAX_UNPINNED_BYTES = 512L * 1024L * 1024L
    }
}
