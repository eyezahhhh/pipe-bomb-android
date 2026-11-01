package net.pipebomb.android.playback.auto

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.di.ApplicationScope
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackSessionActions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class AutoLibrarySessionCallback @Inject constructor(
    private val dataSource: AutoLibraryDataSource,
    private val queueResolver: AutoQueueResolver,
    private val playbackController: PlaybackController,
    private val actions: PlaybackSessionActions,
    @ApplicationScope private val scope: CoroutineScope,
) : MediaLibraryService.MediaLibrarySession.Callback {

    override fun onDisconnected(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ) {
        AppLog.info(
            category = LogCategory.AUTO,
            event = "auto.disconnect",
            fields = mapOf(
                "package" to controller.packageName,
                "uid" to controller.uid,
            ),
        )
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        AppLog.info(
            category = LogCategory.AUTO,
            event = "auto.connect",
            fields = mapOf(
                "package" to controller.packageName,
                "uid" to controller.uid,
            ),
        )
        val defaults = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
        val commands = defaults.availableSessionCommands.buildUpon().apply {
            add(SessionCommand(PlaybackSessionActions.COMMAND_SAVE, Bundle.EMPTY))
            add(SessionCommand(PlaybackSessionActions.COMMAND_SHUFFLE, Bundle.EMPTY))
            add(SessionCommand(PlaybackSessionActions.COMMAND_REPEAT, Bundle.EMPTY))
        }.build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(commands)
            .setAvailablePlayerCommands(defaults.availablePlayerCommands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            PlaybackSessionActions.COMMAND_SAVE -> actions.toggleSaved()
            PlaybackSessionActions.COMMAND_SHUFFLE -> actions.toggleShuffle()
            PlaybackSessionActions.COMMAND_REPEAT -> actions.cycleRepeat()
            else -> return Futures.immediateFuture(
                SessionResult(SessionError.ERROR_NOT_SUPPORTED),
            )
        }
        AppLog.info(
            category = LogCategory.AUTO,
            event = "auto.command",
            fields = mapOf("command" to customCommand.customAction),
        )
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

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
        val nodeName = node::class.simpleName.orEmpty()
        val startedNs = System.nanoTime()
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            runCatching {
                dataSource.children(node, page.coerceAtLeast(0), pageSize.takeIf { it > 0 } ?: 30)
            }.onSuccess { items ->
                AppLog.debug(
                    category = LogCategory.AUTO,
                    event = "auto.browse",
                    fields = mapOf(
                        "node" to nodeName,
                        "page" to page,
                        "pageSize" to pageSize,
                        "results" to items.size,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }.onFailure { error ->
                AppLog.warn(
                    category = LogCategory.AUTO,
                    event = "auto.browse.error",
                    message = error.message,
                    fields = mapOf(
                        "node" to nodeName,
                        "page" to page,
                        "exception" to error.javaClass.simpleName,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
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
        val nodeName = node::class.simpleName.orEmpty()
        val startedNs = System.nanoTime()
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            runCatching { dataSource.item(node) }
                .onSuccess { item ->
                    AppLog.debug(
                        category = LogCategory.AUTO,
                        event = "auto.item",
                        fields = mapOf(
                            "node" to nodeName,
                            "found" to (item != null),
                            "durMs" to elapsedMs(startedNs),
                        ),
                    )
                    if (item == null) {
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                    } else {
                        future.set(LibraryResult.ofItem(item, null))
                    }
                }
                .onFailure { error ->
                    AppLog.warn(
                        category = LogCategory.AUTO,
                        event = "auto.item.error",
                        message = error.message,
                        fields = mapOf(
                            "node" to nodeName,
                            "exception" to error.javaClass.simpleName,
                            "durMs" to elapsedMs(startedNs),
                        ),
                    )
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
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
        val startedNs = System.nanoTime()
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            runCatching {
                dataSource.searchItems(
                    query = query,
                    page = page.coerceAtLeast(0),
                    pageSize = pageSize.takeIf { it > 0 } ?: 30,
                )
            }.onSuccess { items ->
                AppLog.info(
                    category = LogCategory.AUTO,
                    event = "auto.search",
                    fields = mapOf(
                        "queryLength" to query.length,
                        "page" to page,
                        "pageSize" to pageSize,
                        "results" to items.size,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }.onFailure { error ->
                AppLog.warn(
                    category = LogCategory.AUTO,
                    event = "auto.search.error",
                    message = error.message,
                    fields = mapOf(
                        "queryLength" to query.length,
                        "exception" to error.javaClass.simpleName,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
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
        AppLog.info(
            category = LogCategory.AUTO,
            event = "auto.set_media_items",
            fields = mapOf(
                "node" to node::class.simpleName.orEmpty(),
                "count" to mediaItems.size,
                "startIndex" to startIndex,
            ),
        )
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            resolveSelection(node, startPositionMs)
                .onSuccess { queue ->
                    AppLog.debug(
                        category = LogCategory.AUTO,
                        event = "auto.queue",
                        fields = mapOf(
                            "tracks" to queue.tracks.size,
                            "startIndex" to queue.startIndex,
                        ),
                    )
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            ImmutableList.copyOf(queue.items),
                            queue.startIndex,
                            startPositionMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                        ),
                    )
                }
                .onFailure { error ->
                    AppLog.warn(
                        category = LogCategory.AUTO,
                        event = "auto.set_media_items.error",
                        message = error.message,
                        fields = mapOf(
                            "node" to node::class.simpleName.orEmpty(),
                            "exception" to error.javaClass.simpleName,
                        ),
                    )
                    future.setException(error)
                }
        }
        return future
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        scope.launch {
            val resolved = mediaItems.map { item ->
                val node = AutoIds.parse(item.mediaId)
                if (node is AutoNode.Track) {
                    dataSource.tracksByKeys(listOf(node.key)).firstOrNull()
                        ?.let { AutoMediaItems.trackQueued(it, node.context) }
                        ?: item
                } else {
                    item
                }
            }
            AppLog.debug(
                category = LogCategory.AUTO,
                event = "auto.add_media_items",
                fields = mapOf(
                    "requested" to mediaItems.size,
                    "resolved" to resolved.size,
                ),
            )
            future.set(resolved)
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
            AppLog.info(
                category = LogCategory.AUTO,
                event = "auto.resumption",
                fields = mapOf("result" to "empty_queue", "forPlayback" to isForPlayback),
            )
            return Futures.immediateFailedFuture(IllegalStateException("No queue to resume"))
        }
        val startedNs = System.nanoTime()
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            runCatching {
                val tracks = dataSource.tracksByKeys(state.queue)
                val resolved = queueResolver.resolve(
                    tracks = tracks,
                    requestedIndex = state.currentIndex,
                    context = AutoContext.History,
                ) ?: error("Unable to prepare resumption queue")
                if (isForPlayback) {
                    withContext(Dispatchers.Main) {
                        playbackController.prepareExternalQueue(resolved.tracks, resolved.startIndex)
                    }
                }
                MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.copyOf(resolved.items),
                    resolved.startIndex,
                    state.positionMs,
                )
            }.onSuccess { result ->
                AppLog.info(
                    category = LogCategory.AUTO,
                    event = "auto.resumption",
                    fields = mapOf(
                        "result" to "success",
                        "forPlayback" to isForPlayback,
                        "tracks" to result.mediaItems.size,
                        "startIndex" to result.startIndex,
                        "durMs" to elapsedMs(startedNs),
                    ),
                )
                future.set(result)
            }
                .onFailure { error ->
                    AppLog.warn(
                        category = LogCategory.AUTO,
                        event = "auto.resumption",
                        message = error.message,
                        fields = mapOf(
                            "result" to "failed",
                            "forPlayback" to isForPlayback,
                            "exception" to error.javaClass.simpleName,
                            "durMs" to elapsedMs(startedNs),
                        ),
                    )
                    future.setException(error)
                }
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
        withContext(Dispatchers.Main) {
            playbackController.prepareExternalQueue(resolved.tracks, resolved.startIndex)
        }
        resolved
    }

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000
}
