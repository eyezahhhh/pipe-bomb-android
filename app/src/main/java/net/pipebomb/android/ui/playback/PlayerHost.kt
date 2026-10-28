package net.pipebomb.android.ui.playback

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.feature.downloads.DownloadHostState
import net.pipebomb.android.feature.player.NowPlayingContent

@Composable
fun PlayerHost(
    playbackHost: PlaybackHostViewModel,
    miniRect: Rect?,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onOpenArtist: (String) -> Unit,
    onClearQueue: () -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    downloadState: DownloadHostState,
    isOnline: Boolean,
    onDownload: (Track) -> Unit,
    onRemoveDownload: (net.pipebomb.android.data.download.DownloadItem) -> Unit,
) {
    val queueState by playbackHost.queueState.collectAsStateWithLifecycle()
    val tracksByKey by playbackHost.queueTracks.collectAsStateWithLifecycle()
    val track by playbackHost.currentTrack.collectAsStateWithLifecycle()
    val state by playbackHost.state.collectAsStateWithLifecycle()
    val isPlaying by playbackHost.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by playbackHost.isBuffering.collectAsStateWithLifecycle()
    val savedTracks by playbackHost.savedTracks.collectAsStateWithLifecycle()

    if (queueState.queue.isEmpty()) return

    val density = LocalDensity.current
    val motion = PipeBombTheme.motion
    val colors = PipeBombTheme.colors
    val scope = rememberCoroutineScope()
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var progress by remember { mutableStateOf(if (open) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var gestureSettling by remember { mutableStateOf(false) }
    var progressJob by remember { mutableStateOf<Job?>(null) }
    val latestOpen by rememberUpdatedState(open)

    val fullVisible by remember { derivedStateOf { progress > 0.02f || latestOpen } }
    val miniVisible by remember { derivedStateOf { !latestOpen || progress < 0.45f } }

    val mini = miniRect
    val fullWidth = hostSize.width.toFloat()
    val fullHeight = hostSize.height.toFloat()
    val expandDistance = ((mini?.top ?: 0f).takeIf { it > 0f } ?: (fullHeight * 0.35f))
        .coerceAtLeast(1f)
    val latestExpandDistance by rememberUpdatedState(expandDistance)

    fun animateProgressTo(
        target: Float,
        initialVelocity: Float = 0f,
        fromGesture: Boolean = false,
    ) {
        progressJob?.cancel()
        if (fromGesture) gestureSettling = true
        val job = scope.launch {
            try {
                animate(
                    initialValue = progress,
                    targetValue = target,
                    initialVelocity = initialVelocity,
                    animationSpec = if (initialVelocity != 0f) {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    } else {
                        tween(
                            durationMillis = if (target > progress) motion.enterMillis else motion.exitMillis,
                            easing = motion.standard,
                        )
                    },
                ) { value, _ -> progress = value }
            } finally {
                if (fromGesture) gestureSettling = false
            }
        }
        progressJob = job
    }

    fun settleFromGesture(velocity: Float) {
        if (!dragging) return
        dragging = false
        val progressVelocity = (-velocity / expandDistance).coerceIn(-8f, 8f)
        val projected = (progress + progressVelocity * GESTURE_PROJECTION_SECONDS).coerceIn(0f, 1f)
        val shouldOpen = when {
            velocity <= -SETTLE_FLING_THRESHOLD -> true
            velocity >= SETTLE_FLING_THRESHOLD -> false
            else -> projected >= SETTLE_POSITION_THRESHOLD
        }
        if (shouldOpen != open) onOpenChange(shouldOpen)
        animateProgressTo(
            target = if (shouldOpen) 1f else 0f,
            initialVelocity = progressVelocity,
            fromGesture = true,
        )
    }

    LaunchedEffect(open) {
        if (!dragging && !gestureSettling) {
            animateProgressTo(if (open) 1f else 0f)
        }
    }

    LaunchedEffect(track) {
        if (track == null && open) onOpenChange(false)
    }

    val expandDragState = rememberDraggableState { delta ->
        if (!latestOpen) {
            dragging = true
            progressJob?.cancel()
            progress = (progress - delta / latestExpandDistance).coerceIn(0f, 1f)
        }
    }

    fun collapseDrag(delta: Float) {
        dragging = true
        progressJob?.cancel()
        progress = (progress - delta / expandDistance).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { hostSize = it },
    ) {
        if (mini != null && hostSize.width > 0 && hostSize.height > 0) {
            val bg = colors.bgSurface
            val miniWidth = with(density) { mini.width.toDp() }
            val miniHeight = with(density) { mini.height.toDp() }
            val currentKey = queueState.queue.getOrNull(queueState.currentIndex)
            val isSaved = currentKey?.let { key ->
                savedTracks[key] ?: tracksByKey[key]?.bookmarked
            } == true
            val downloadItem = currentKey?.let { downloadState.byTrack[it.toString()] }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(colors.overlayScrim),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val p = progress
                        val left = lerp(mini.left, 0f, p)
                        val top = lerp(mini.top, 0f, p)
                        val right = lerp(mini.right, fullWidth, p)
                        val bottom = lerp(mini.bottom, fullHeight, p)
                        val topRadius = lerp(8f, 24f, p) * density.density
                        val bottomRadius = lerp(8f, 0f, p) * density.density
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(left, top, right, bottom),
                                    topLeft = CornerRadius(topRadius),
                                    topRight = CornerRadius(topRadius),
                                    bottomRight = CornerRadius(bottomRadius),
                                    bottomLeft = CornerRadius(bottomRadius),
                                ),
                            )
                        }
                        drawPath(path, bg)
                        clipPath(path) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                if (fullVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = progress
                                translationX = lerp(mini.left, 0f, p)
                                translationY = lerp(mini.top, 0f, p)
                                alpha = ((p - 0.1f) / 0.4f).coerceIn(0f, 1f)
                            },
                    ) {
                        NowPlayingContent(
                            track = track,
                            state = state,
                            queue = queueState.queue,
                            currentIndex = queueState.currentIndex,
                            tracksByKey = tracksByKey,
                            onEnsureTracks = playbackHost::ensureQueueTracks,
                            onSelectIndex = playbackHost::playIndex,
                            onToggle = playbackHost::toggle,
                            onNext = playbackHost::next,
                            onPrevious = playbackHost::previous,
                            onSeek = playbackHost::seekTo,
                            onShuffle = playbackHost::toggleShuffle,
                            onRepeat = playbackHost::cycleRepeat,
                            onDismiss = { onOpenChange(false) },
                            onCollapseDrag = ::collapseDrag,
                            onCollapseDragEnd = ::settleFromGesture,
                            collapseEnabled = open,
                            onClearQueue = onClearQueue,
                            onRemoveFromQueue = onRemoveFromQueue,
                            isOnline = isOnline,
                            isSaved = isSaved,
                            onToggleSave = playbackHost::toggleSaved,
                            downloadItem = downloadItem,
                            downloadsAvailable = downloadState.storageAvailable && isOnline,
                            onDownload = { track?.let(onDownload) },
                            onRemoveDownload = { downloadItem?.let(onRemoveDownload) },
                            onOpenArtist = onOpenArtist,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (!open && fullVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            },
                    )
                }

                if (miniVisible) {
                    Box(
                        modifier = Modifier
                            .size(miniWidth, miniHeight)
                            .graphicsLayer {
                                val p = progress
                                translationX = lerp(mini.left, 0f, p)
                                translationY = lerp(mini.top, 0f, p)
                                alpha = (1f - p / 0.25f).coerceIn(0f, 1f)
                            }
                            .then(
                                if (open) {
                                    Modifier.pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = expandDragState,
                                enabled = !open,
                                onDragStarted = {
                                    dragging = true
                                    progressJob?.cancel()
                                },
                                onDragStopped = { velocity -> settleFromGesture(velocity) },
                            ),
                    ) {
                        MiniPlayerCard(
                            queue = queueState.queue,
                            currentIndex = queueState.currentIndex,
                            tracksByKey = tracksByKey,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onOpen = { if (!open) onOpenChange(true) },
                            onToggle = playbackHost::toggle,
                            onSelectIndex = playbackHost::playIndex,
                            onEnsureTracks = playbackHost::ensureQueueTracks,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private const val SETTLE_FLING_THRESHOLD = 700f
private const val SETTLE_POSITION_THRESHOLD = 0.35f
private const val GESTURE_PROJECTION_SECONDS = 0.12f
