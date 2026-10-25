package net.pipebomb.android.ui.playback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonStyle
import net.pipebomb.android.ui.components.ResourceImage
import kotlin.math.abs

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxDrag = with(density) { 64.dp.toPx() }
    val swipeThreshold = with(density) { 40.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val displayedOffset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else 0f,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "miniPlayerSwipe",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PipeBombTheme.spacing.sm, vertical = PipeBombTheme.spacing.xs)
            .height(64.dp)
            .graphicsLayer {
                translationX = displayedOffset
                alpha = 1f - (abs(displayedOffset) / maxDrag) * 0.15f
            }
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .testTag("mini-player")
            .clickable(onClick = onOpen)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragging = true
                    dragOffset = (dragOffset + delta).coerceIn(-maxDrag, maxDrag)
                },
                onDragStarted = {
                    dragging = true
                    dragOffset = 0f
                },
                onDragStopped = { velocity ->
                    val shouldNext = dragOffset < -swipeThreshold || velocity < -900f
                    val shouldPrevious = dragOffset > swipeThreshold || velocity > 900f
                    dragging = false
                    dragOffset = 0f
                    when {
                        shouldNext -> onNext()
                        shouldPrevious -> onPrevious()
                    }
                },
            )
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        ResourceImage(
            resource = DisplayText.artwork(track),
            modifier = Modifier.size(48.dp).clip(PipeBombTheme.shapes.small),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = DisplayText.title(track),
                style = PipeBombTheme.typography.trackTitle,
                color = PipeBombTheme.colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DisplayText.artists(track),
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PipeBombIconButton(
            icon = ImageVector.vectorResource(
                if (isPlaying) R.drawable.ic_tabler_pause_filled else R.drawable.ic_tabler_play_filled,
            ),
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onToggle,
            style = PipeBombIconButtonStyle.Background,
            loading = isBuffering,
        )
    }
}
