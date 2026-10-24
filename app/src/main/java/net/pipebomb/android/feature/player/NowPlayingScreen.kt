package net.pipebomb.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.playback.PlaybackUiState
import net.pipebomb.android.playback.RepeatMode
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonStyle
import net.pipebomb.android.ui.components.PipeBombIconButtonTone
import net.pipebomb.android.ui.components.PipeBombProgressTrack
import net.pipebomb.android.ui.components.ResourceImage

@Composable
fun NowPlayingScreen(
    track: Track?,
    state: PlaybackUiState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit = onPrevious,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onClose: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = PipeBombTheme.colors
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val dismissThreshold = with(density) { 96.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val displayedOffset by animateOffsetAsState(
        targetValue = if (dragging) dragOffset else Offset.Zero,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "playerDrag",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PipeBombTheme.spacing.lg)
            .graphicsLayer {
                translationX = displayedOffset.x
                translationY = displayedOffset.y
                alpha = (1f - (displayedOffset.y / dismissThreshold) * 0.45f).coerceIn(0.35f, 1f)
            },
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

        Spacer(Modifier.weight(1f))

        if (track != null) {
            val artistId = track.artists?.firstOrNull()?.artist?.let { it.uuid ?: it.identityKey }
            ResourceImage(
                resource = DisplayText.artwork(track),
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clip(PipeBombTheme.shapes.large)
                    .pointerInput(track) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                dragOffset = Offset.Zero
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                            },
                            onDragEnd = {
                                val total = dragOffset

                                dragging = false
                                dragOffset = Offset.Zero
                                val horizontal = abs(total.x) > abs(total.y)
                                when {
                                    horizontal && total.x < -swipeThreshold -> onNext()
                                    horizontal && total.x > swipeThreshold -> onSwipePrevious()
                                    !horizontal && total.y > dismissThreshold -> onClose()
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = Offset.Zero
                            },
                        )
                    },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = PipeBombTheme.spacing.xl),
            ) {
                Text(
                    text = DisplayText.title(track),
                    style = PipeBombTheme.typography.sectionHeading,
                    color = colors.fgPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
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
        } else {
            Text(
                text = "Nothing playing",
                style = PipeBombTheme.typography.sectionHeading,
                color = colors.fgTertiary,
            )
        }

        Spacer(Modifier.weight(1f))

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

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val remaining = total % 60
    return "$minutes:${remaining.toString().padStart(2, '0')}"
}
