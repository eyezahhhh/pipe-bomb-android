package net.pipebomb.android.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.userMessage
import java.io.IOException

@OptIn(UnstableApi::class)
fun interface TrackMediaResolver {
    suspend fun resolve(key: TrackKey): net.pipebomb.android.core.net.ApiResult<ResolvedMedia>
}

@OptIn(UnstableApi::class)
class LazyTrackMediaSourceFactory(
    private val resolver: TrackMediaResolver,
    private val childFactory: MediaSource.Factory,
    private val scope: CoroutineScope,
) : MediaSource.Factory {
    constructor(
        resolver: PlaybackResolver,
        childFactory: MediaSource.Factory,
        scope: CoroutineScope,
    ) : this(
        resolver = TrackMediaResolver { key -> resolver.resolve(key) },
        childFactory = childFactory,
        scope = scope,
    )

    override fun getSupportedTypes(): IntArray =
        intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = this

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val syntheticKey = mediaItem.localConfiguration?.uri?.let(TrackMediaUri::parse)
        return if (syntheticKey == null) {
            childFactory.createMediaSource(mediaItem)
        } else {
            LazyTrackMediaSource(
                mediaItem = mediaItem,
                key = syntheticKey,
                resolver = resolver,
                childFactory = childFactory,
                scope = scope,
            )
        }
    }
}

@OptIn(UnstableApi::class)
private class LazyTrackMediaSource(
    private val mediaItem: MediaItem,
    private val key: TrackKey,
    private val resolver: TrackMediaResolver,
    private val childFactory: MediaSource.Factory,
    private val scope: CoroutineScope,
) : CompositeMediaSource<String>() {
    private var handler: Handler? = null
    private var resolveJob: Job? = null
    private var childSource: MediaSource? = null
    private var resolutionError: IOException? = null
    @Volatile private var released = false

    override fun getMediaItem(): MediaItem = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val playbackHandler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
        handler = playbackHandler
        resolveJob = scope.launch(Dispatchers.IO) {
            val result = resolver.resolve(key)
            playbackHandler.post {
                if (released) return@post
                when (result) {
                    is net.pipebomb.android.core.net.ApiResult.Failure -> {
                        resolutionError = IOException(result.error.userMessage())
                        refreshSourceInfo(
                            SinglePeriodTimeline(C.TIME_UNSET, false, false, false, null, mediaItem),
                        )
                    }

                    is net.pipebomb.android.core.net.ApiResult.Success -> {
                        val resolvedItem = TrackMediaItemFactory.withResolvedSource(mediaItem, result.value)
                        val source = childFactory.createMediaSource(resolvedItem)
                        childSource = source
                        prepareChildSource(key.toString(), source)
                    }
                }
            }
        }
    }

    override fun onChildSourceInfoRefreshed(
        childSourceId: String,
        mediaSource: MediaSource,
        newTimeline: Timeline,
    ) {
        refreshSourceInfo(SyntheticTrackTimeline(newTimeline, mediaItem))
    }

    override fun maybeThrowSourceInfoRefreshError() {
        resolutionError?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod {
        val source = childSource
        if (source == null) {
            return FailingMediaPeriod(
                resolutionError ?: IOException("Track source was not resolved"),
            )
        }
        return source.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        childSource?.releasePeriod(mediaPeriod)
    }

    private class SyntheticTrackTimeline(
        timeline: Timeline,
        private val mediaItem: MediaItem,
    ) : ForwardingTimeline(timeline) {
        override fun getWindow(
            windowIndex: Int,
            window: Timeline.Window,
            defaultPositionUs: Long,
        ): Timeline.Window {
            super.getWindow(windowIndex, window, defaultPositionUs)
            window.mediaItem = mediaItem
            return window
        }
    }

    private class FailingMediaPeriod(
        private val error: IOException,
    ) : MediaPeriod {
        override fun prepare(callback: MediaPeriod.Callback, startPositionUs: Long) = Unit

        override fun maybeThrowPrepareError() {
            throw error
        }

        override fun getTrackGroups(): TrackGroupArray = TrackGroupArray.EMPTY

        override fun selectTracks(
            selections: Array<out ExoTrackSelection?>,
            mayRetainStreamFlags: BooleanArray,
            streams: Array<out SampleStream?>,
            streamResetFlags: BooleanArray,
            positionUs: Long,
        ): Long = positionUs

        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) = Unit

        override fun readDiscontinuity(): Long = C.TIME_UNSET

        override fun seekToUs(positionUs: Long): Long = positionUs

        override fun getAdjustedSeekPositionUs(
            positionUs: Long,
            seekParameters: SeekParameters,
        ): Long = positionUs

        override fun getBufferedPositionUs(): Long = C.TIME_END_OF_SOURCE

        override fun getNextLoadPositionUs(): Long = C.TIME_END_OF_SOURCE

        override fun continueLoading(loadingInfo: LoadingInfo): Boolean = false

        override fun isLoading(): Boolean = false

        override fun reevaluateBuffer(positionUs: Long) = Unit
    }

    override fun releaseSourceInternal() {
        released = true
        resolveJob?.cancel()
        childSource = null
        resolutionError = null
        handler = null
        super.releaseSourceInternal()
    }
}
