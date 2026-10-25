package net.pipebomb.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.playback.PlaybackUiState
import net.pipebomb.android.playback.RepeatMode
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonStyle
import net.pipebomb.android.ui.components.PipeBombIconButtonTone
import net.pipebomb.android.ui.components.PipeBombProgressTrack
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.playback.TrackPagerEffects

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    track: Track?,
    state: PlaybackUiState,
    queue: List<TrackKey>,
    currentIndex: Int,
    tracksByKey: Map<TrackKey, Track>,
    onEnsureTracks: (Iterable<Int>) -> Unit,
    onSelectIndex: (Int) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    downloadItem: DownloadItem? = null,
    downloadsAvailable: Boolean = true,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
) {
    val colors = PipeBombTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pageCount = queue.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    fun dismissThen(action: () -> Unit) {
        scope.launch {
            runCatching { sheetState.hide() }
            if (!sheetState.isVisible) action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgSurface,
        contentColor = colors.fgPrimary,
        scrimColor = colors.overlayScrim,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        sheetGesturesEnabled = !pagerState.isScrollInProgress,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        NowPlayingContent(
            track = track,
            state = state,
            queue = queue,
            currentIndex = currentIndex,
            pagerState = pagerState,
            tracksByKey = tracksByKey,
            onEnsureTracks = onEnsureTracks,
            onSelectIndex = onSelectIndex,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            onClose = { dismissThen(onDismiss) },
            onOpenQueue = { dismissThen(onOpenQueue) },
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            downloadItem = downloadItem,
            downloadsAvailable = downloadsAvailable,
            onDownload = onDownload,
            onRemoveDownload = onRemoveDownload,
            onOpenArtist = { artistId -> dismissThen { onOpenArtist(artistId) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingContent(
    track: Track?,
    state: PlaybackUiState,
    queue: List<TrackKey>,
    currentIndex: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    tracksByKey: Map<TrackKey, Track>,
    onEnsureTracks: (Iterable<Int>) -> Unit,
    onSelectIndex: (Int) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    downloadItem: DownloadItem?,
    downloadsAvailable: Boolean,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val colors = PipeBombTheme.colors
    TrackPagerEffects(
        pagerState = pagerState,
        currentIndex = currentIndex,
        itemCount = queue.size,
        onEnsureTracks = onEnsureTracks,
        onSelectIndex = onSelectIndex,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PipeBombTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = PipeBombTheme.spacing.sm, bottom = PipeBombTheme.spacing.xs)
                .size(width = 40.dp, height = 4.dp)
                .clip(PipeBombTheme.shapes.pill)
                .background(colors.borderStrong),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_x),
                contentDescription = "Close",
                onClick = onClose,
                tone = PipeBombIconButtonTone.Neutral,
            )
            Text(
                text = "Now playing",
                style = PipeBombTheme.typography.metadata,
                color = colors.fgTertiary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val downloadIcon = when (downloadItem?.state) {
                    DownloadState.Completed -> R.drawable.ic_tabler_check_filled
                    DownloadState.Failed -> R.drawable.ic_tabler_refresh
                    else -> R.drawable.ic_tabler_download
                }
                val downloadDescription = when (downloadItem?.state) {
                    DownloadState.Completed -> "Remove download"
                    DownloadState.Failed -> "Retry download"
                    null -> "Download"
                    else -> "Cancel download"
                }
                PipeBombIconButton(
                    icon = ImageVector.vectorResource(downloadIcon),
                    contentDescription = downloadDescription,
                    onClick = {
                        when (downloadItem?.state) {
                            null, DownloadState.Failed -> onDownload()
                            else -> onRemoveDownload()
                        }
                    },
                    tone = if (downloadItem?.state == DownloadState.Completed) {
                        PipeBombIconButtonTone.Accent
                    } else {
                        PipeBombIconButtonTone.Neutral
                    },
                    enabled = downloadItem != null || downloadsAvailable,
                    loading = downloadItem?.state == DownloadState.Downloading,
                )
                PipeBombIconButton(
                    icon = ImageVector.vectorResource(
                        if (isSaved) R.drawable.ic_tabler_bookmark_filled else R.drawable.ic_tabler_bookmark,
                    ),
                    contentDescription = if (isSaved) "Remove from saved" else "Save",
                    onClick = onToggleSave,
                    tone = if (isSaved) PipeBombIconButtonTone.Primary else PipeBombIconButtonTone.Neutral,
                )
                PipeBombIconButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_tabler_sidebar_expand_filled),
                    contentDescription = "Queue",
                    onClick = onOpenQueue,
                    tone = PipeBombIconButtonTone.Neutral,
                )
            }
        }

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing playing",
                    style = PipeBombTheme.typography.sectionHeading,
                    color = colors.fgTertiary,
                )
            }
            return@Column
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("player-pager"),
            beyondViewportPageCount = 1,
        ) { page ->
            val pageKey = queue[page]
            val pageTrack = tracksByKey[pageKey]
                ?: track?.takeIf { page == currentIndex }
            PlayerPage(
                track = pageTrack,
                onOpenArtist = onOpenArtist,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
        ) {
            PipeBombProgressTrack(
                value = state.positionMs / 1000f,
                max = (state.durationMs / 1000f).coerceAtLeast(1f),
                onSeek = { onSeek((it * 1000).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = track != null,
                loading = state.isBuffering,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(state.positionMs / 1000f),
                    style = PipeBombTheme.typography.metadata,
                    color = colors.fgTertiary,
                )
                Text(
                    text = if (state.durationMs > 0) formatTime(state.durationMs / 1000f) else "-:-",
                    style = PipeBombTheme.typography.metadata,
                    color = colors.fgTertiary,
                )
            }
        }

        Row(
            modifier = Modifier.padding(vertical = PipeBombTheme.spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
        ) {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_shuffle),
                contentDescription = "Shuffle",
                onClick = onShuffle,
                tone = if (state.shuffle) PipeBombIconButtonTone.Accent else PipeBombIconButtonTone.Neutral,
            )
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_skip_back_filled),
                contentDescription = "Previous",
                onClick = onPrevious,
            )
            PipeBombIconButton(
                icon = ImageVector.vectorResource(
                    if (state.isPlaying) R.drawable.ic_tabler_pause_filled else R.drawable.ic_tabler_play_filled,
                ),
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                onClick = onToggle,
                size = PipeBombIconButtonSize.ExtraLarge,
                style = PipeBombIconButtonStyle.Background,
            )
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_skip_forward_filled),
                contentDescription = "Next",
                onClick = onNext,
            )
            PipeBombIconButton(
                icon = ImageVector.vectorResource(
                    if (state.repeat == RepeatMode.One) {
                        R.drawable.ic_tabler_repeat_once
                    } else {
                        R.drawable.ic_tabler_repeat
                    },
                ),
                contentDescription = "Repeat",
                onClick = onRepeat,
                tone = if (state.repeat == RepeatMode.Off) {
                    PipeBombIconButtonTone.Neutral
                } else {
                    PipeBombIconButtonTone.Accent
                },
            )
        }

        if (state.error != null) {
            Text(
                text = state.error!!,
                style = PipeBombTheme.typography.metadata,
                color = colors.error,
                modifier = Modifier.padding(bottom = PipeBombTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun PlayerPage(
    track: Track?,
    onOpenArtist: (String) -> Unit,
) {
    val colors = PipeBombTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ResourceImage(
            resource = track?.let { DisplayText.artwork(it) },
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .aspectRatio(1f)
                .clip(PipeBombTheme.shapes.large),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = PipeBombTheme.spacing.xl),
        ) {
            Text(
                text = track?.let { DisplayText.title(it) } ?: "Loading…",
                style = PipeBombTheme.typography.sectionHeading,
                color = if (track == null) colors.fgTertiary else colors.fgPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (track != null) {
                val artistId = track.artists?.firstOrNull()?.artist?.let { it.uuid ?: it.identityKey }
                Text(
                    text = DisplayText.artists(track),
                    style = PipeBombTheme.typography.bodySecondary,
                    color = colors.fgSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (artistId != null) TextDecoration.Underline else null,
                    modifier = if (artistId != null) {
                        Modifier.clickable { onOpenArtist(artistId) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val remaining = total % 60
    return "$minutes:${remaining.toString().padStart(2, '0')}"
}
