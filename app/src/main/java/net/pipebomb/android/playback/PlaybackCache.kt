
package net.pipebomb.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
object TrackCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String {
        dataSpec.key?.let { return it }
        val segments = dataSpec.uri.pathSegments
        val hlsIndex = segments.indexOf("hls")
        val stableSegments = if (hlsIndex >= 0) {
            segments.drop(hlsIndex)
        } else {
            segments.takeLast(2)
        }
        return "uri:" + stableSegments.joinToString("/")
    }
}

@Singleton
@OptIn(UnstableApi::class)
class PlaybackCache @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val cacheDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "media")
    private val databaseProvider = StandaloneDatabaseProvider(context)

    val cache: SimpleCache = SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        databaseProvider,
    )

    val dataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setCacheWriteDataSinkFactory(
            CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
        )
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        .setCacheKeyFactory(TrackCacheKeyFactory)

    fun clear() {
        cache.keys.toList().forEach { key ->
            cache.removeResource(key)
        }
    }

    fun sizeBytes(): Long = cache.cacheSpace

    private companion object {
        const val MAX_BYTES = 512L * 1024L * 1024L
    }
}
