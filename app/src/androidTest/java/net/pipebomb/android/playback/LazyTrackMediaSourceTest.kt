package net.pipebomb.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class LazyTrackMediaSourceTest {
    @Test
    fun resolutionFailurePublishesAnEmptyTimelineAndSurfacesTheError() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(
            instrumentation.targetContext,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val key = TrackKey("plugin", "library", "track")
        val source = LazyTrackMediaSourceFactory(
            resolver = failingResolver(),
            childFactory = unusedChildFactory(),
            scope = scope,
        ).createMediaSource(syntheticItem(key))
        val publishedTimeline = arrayOfNulls<Timeline>(1)
        val caller = object : MediaSource.MediaSourceCaller {
            override fun onSourceInfoRefreshed(source: MediaSource, timeline: Timeline) {
                publishedTimeline[0] = timeline
            }
        }

        try {
            instrumentation.runOnMainSync {
                source.prepareSource(caller, PlayerId.UNSET, bandwidthMeter)
            }

            val deadline = System.currentTimeMillis() + 5_000
            var error: IOException? = null
            while (System.currentTimeMillis() < deadline && error == null) {
                instrumentation.runOnMainSync {
                    try {
                        source.maybeThrowSourceInfoRefreshError()
                    } catch (caught: IOException) {
                        error = caught
                    }
                }
                if (error == null) Thread.sleep(50)
            }

            if (error == null) fail("Resolution failure was not surfaced")
            assertTrue(
                "Failure timeline should be published",
                publishedTimeline[0]?.isEmpty == false,
            )
            var prepareError: IOException? = null
            instrumentation.runOnMainSync {
                val period = source.createPeriod(
                    MediaSource.MediaPeriodId("test"),
                    DefaultAllocator(true, 64 * 1024),
                    0,
                )
                try {
                    period.maybeThrowPrepareError()
                } catch (caught: IOException) {
                    prepareError = caught
                }
            }
            assertNotNull("Failed period did not surface the error", prepareError)
        } finally {
            instrumentation.runOnMainSync { source.releaseSource(caller) }
            scope.cancel()
        }
    }

    @Test
    fun failedResolutionReachesThePlayerAsAnErrorInsteadOfCrashing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val key = TrackKey("plugin", "library", "track")
        val factory = LazyTrackMediaSourceFactory(
            resolver = failingResolver(),
            childFactory = unusedChildFactory(),
            scope = scope,
        )
        val error = AtomicReference<PlaybackException?>(null)
        val latch = CountDownLatch(1)
        val player = AtomicReference<ExoPlayer?>(null)

        try {
            instrumentation.runOnMainSync {
                val exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(factory)
                    .build()
                player.set(exoPlayer)
                exoPlayer.addListener(
                    object : Player.Listener {
                        override fun onPlayerError(playerError: PlaybackException) {
                            error.set(playerError)
                            latch.countDown()
                        }
                    },
                )
                exoPlayer.setMediaItem(syntheticItem(key))
                exoPlayer.prepare()
            }

            assertTrue(
                "Player did not report the resolution failure",
                latch.await(5, TimeUnit.SECONDS),
            )
            assertNotNull(error.get())
        } finally {
            instrumentation.runOnMainSync { player.get()?.release() }
            scope.cancel()
        }
    }

    private fun failingResolver(): TrackMediaResolver = TrackMediaResolver {
        ApiResult.Failure(ApiError.Network("offline"))
    }

    private fun syntheticItem(key: TrackKey): MediaItem = MediaItem.Builder()
        .setMediaId("test")
        .setUri(TrackMediaUri.build(key))
        .build()

    private fun unusedChildFactory(): MediaSource.Factory = object : MediaSource.Factory {
        override fun getSupportedTypes(): IntArray = intArrayOf()

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider,
        ): MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
        ): MediaSource.Factory = this

        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            error("Child source must not be created for a failed resolution")
    }
}
