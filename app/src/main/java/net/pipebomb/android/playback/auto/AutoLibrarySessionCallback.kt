package net.pipebomb.android.playback.auto

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.pipebomb.android.di.ApplicationScope
import net.pipebomb.android.playback.PlaybackController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class AutoLibrarySessionCallback @Inject constructor(
    private val dataSource: AutoLibraryDataSource,
    private val queueResolver: AutoQueueResolver,
    private val playbackController: PlaybackController,
    @ApplicationScope private val scope: CoroutineScope,
) : MediaLibraryService.MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val extras = Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, 2)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, 1)
        }
        val libraryParams = MediaLibraryService.LibraryParams.Builder().setExtras(extras).build()
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                AutoMediaItems.category(AutoIds.ROOT, "Pipe Bomb", browsable = true),
                libraryParams,
            ),
        )
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val node = AutoIds.parse(parentId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            runCatching {
                dataSource.children(node, page.coerceAtLeast(0), pageSize.takeIf { it > 0 } ?: 30)
            }.onSuccess { items ->
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }.onFailure {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val node = AutoIds.parse(mediaId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            runCatching { dataSource.item(node) }
                .onSuccess { item ->
                    if (item == null) {
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                    } else {
                        future.set(LibraryResult.ofItem(item, null))
                    }
                }
                .onFailure { future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN)) }
        }
        return future
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> =
        Futures.immediateFuture(LibraryResult.ofVoid(params))

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            runCatching {
                dataSource.searchItems(
                    query = query,
                    page = page.coerceAtLeast(0),
                    pageSize = pageSize.takeIf { it > 0 } ?: 30,
                )
            }.onSuccess { items ->
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }.onFailure {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val node = AutoIds.parse(mediaItems.firstOrNull()?.mediaId ?: "")
            ?: return Futures.immediateFailedFuture(IllegalArgumentException("Unknown media id"))
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            resolveSelection(node, startPositionMs)
                .onSuccess { queue ->
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            ImmutableList.copyOf(queue.items),
                            queue.startIndex,
                            startPositionMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                        ),
                    )
                }
                .onFailure { future.setException(it) }
        }
        return future
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val node = AutoIds.parse(mediaItems.firstOrNull()?.mediaId ?: "")
            ?: return Futures.immediateFailedFuture(IllegalArgumentException("Unknown media id"))
        val future = SettableFuture.create<List<MediaItem>>()
        scope.launch {
            resolveSelection(node, 0)
                .onSuccess { queue ->
                    val rotatedItems = queue.items.drop(queue.startIndex) + queue.items.take(queue.startIndex)
                    val rotatedTracks = queue.tracks.drop(queue.startIndex) + queue.tracks.take(queue.startIndex)
                    playbackController.prepareExternalQueue(rotatedTracks, 0)
                    future.set(rotatedItems)
                }
                .onFailure { future.setException(it) }
        }
        return future
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val state = playbackController.state.value
        if (state.queue.isEmpty()) {
            return Futures.immediateFailedFuture(IllegalStateException("No queue to resume"))
        }
        if (!isForPlayback) {
            val track = playbackController.currentTrack.value
            val item = track?.let {
                AutoMediaItems.trackBrowse(it, AutoContext.History)
            } ?: MediaItem.Builder()
                .setMediaId(AutoIds.HISTORY)
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Pipe Bomb").build())
                .build()
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.of(item),
                    0,
                    state.positionMs,
                ),
            )
        }
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            runCatching {
                val tracks = dataSource.tracksByKeys(state.queue)
                val resolved = queueResolver.resolve(
                    tracks = tracks,
                    requestedIndex = state.currentIndex,
                    context = AutoContext.History,
                ) ?: error("Unable to prepare resumption queue")
                playbackController.prepareExternalQueue(resolved.tracks, resolved.startIndex)
                MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.copyOf(resolved.items),
                    resolved.startIndex,
                    state.positionMs,
                )
            }.onSuccess { future.set(it) }
                .onFailure { future.setException(it) }
        }
        return future
    }

    private suspend fun resolveSelection(
        node: AutoNode,
        startPositionMs: Long,
    ): Result<AutoResolvedQueue> = runCatching {
        val request = dataSource.queueRequest(node) ?: error("Nothing playable")
        val context = when (node) {
            is AutoNode.Track -> node.context
            else -> AutoIds.contextFor(node) ?: AutoContext.History
        }
        val resolved = queueResolver.resolve(request.tracks, request.startIndex, context)
            ?: error("Unable to resolve queue")
        playbackController.prepareExternalQueue(resolved.tracks, resolved.startIndex)
        resolved
    }
}
