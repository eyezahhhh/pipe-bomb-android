package net.pipebomb.android.playback.download

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnedStreamCacheEvictorTest {
    @Test
    fun concurrentSpanUpdatesAndPinRefreshRemainThreadSafe() {
        val evictor = PinnedStreamCacheEvictor(
            pins = DownloadPinRegistry(),
            maxUnpinnedBytes = Long.MAX_VALUE,
        )
        val cache = dummyCache()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(6)
        try {
            val writers = (0 until 4).map { writer ->
                executor.submit {
                    start.await()
                    repeat(750) { index ->
                        evictor.onSpanAdded(
                            cache,
                            CacheSpan(
                                "track:writer-$writer:$index:",
                                0,
                                1,
                                index.toLong(),
                                File("/tmp/pipe-bomb-cache-span-$writer-$index"),
                            ),
                        )
                    }
                }
            }
            val readers = (0 until 2).map {
                executor.submit {
                    start.await()
                    repeat(750) { evictor.refreshPins() }
                }
            }

            start.countDown()
            (writers + readers).forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun dummyCache(): Cache {
        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.returnType) {
                java.lang.Long.TYPE -> 0L
                java.lang.Boolean.TYPE -> false
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            Cache::class.java.classLoader,
            arrayOf(Cache::class.java),
            handler,
        ) as Cache
    }
}
