package net.pipebomb.android.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.userMessage
import java.io.IOException

@OptIn(UnstableApi::class)
class LazyTrackMediaSourceFactory(
    private val resolver: PlaybackResolver,
    private val childFactory: MediaSource.Factory,
    private val scope: CoroutineScope,
) : MediaSource.Factory {
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
    private val resolver: PlaybackResolver,
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
            ?: throw IllegalStateException("Track source was not resolved before period creation")
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

    override fun releaseSourceInternal() {
        released = true
        resolveJob?.cancel()
        childSource = null
        resolutionError = null
        handler = null
        super.releaseSourceInternal()
    }
}
