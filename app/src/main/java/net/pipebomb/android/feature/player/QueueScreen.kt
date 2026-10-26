package net.pipebomb.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.playback.RepeatMode
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonTone
import net.pipebomb.android.ui.components.ResourceImage

@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Up Next",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
            )
            PipeBombButton(
                text = "Clear",
                onClick = viewModel::clear,
                variant = PipeBombButtonVariant.Ghost,
                enabled = state.queue.isNotEmpty(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg)) {
            QueueStatus(
                icon = R.drawable.ic_tabler_shuffle,
                label = if (state.shuffle) "Shuffle on" else "Shuffle off",
                active = state.shuffle,
            )
            QueueStatus(
                icon = if (state.repeat == RepeatMode.One) {
                    R.drawable.ic_tabler_repeat_once
                } else {
                    R.drawable.ic_tabler_repeat
                },
                label = when (state.repeat) {
                    RepeatMode.Off -> "Repeat off"
                    RepeatMode.All -> "Repeat all"
                    RepeatMode.One -> "Repeat one"
                },
                active = state.repeat != RepeatMode.Off,
            )
        }
        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "The queue is empty",
                    style = PipeBombTheme.typography.bodySecondary,
                    color = PipeBombTheme.colors.fgTertiary,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                itemsIndexed(state.queue, key = { index, key -> "$index:$key" }) { index, key ->
                    QueueRow(
                        track = tracks[key],
                        position = index + 1,
                        isCurrent = index == state.currentIndex,
                        isPlaying = index == state.currentIndex && state.isPlaying,
                        isOnline = isOnline,
                        onClick = { viewModel.playIndex(index) },
                        onRemove = { viewModel.remove(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueStatus(icon: Int, label: String, active: Boolean) {
    val tint = if (active) PipeBombTheme.colors.accentPrimary else PipeBombTheme.colors.fgTertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = PipeBombTheme.typography.metadata,
            color = tint,
        )
    }
}

@Composable
private fun QueueRow(
    track: Track?,
    position: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isOnline: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(if (isCurrent) colors.accentSubtle else colors.bgSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Text(
            text = position.toString(),
            style = PipeBombTheme.typography.metadata,
            color = colors.fgTertiary,
            modifier = Modifier.size(width = 20.dp, height = 18.dp),
        )
        ResourceImage(
            resource = track?.let { DisplayText.artwork(it) },
            modifier = Modifier.size(44.dp).clip(PipeBombTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = track?.let { DisplayText.title(it) }
                    ?: if (isOnline) "Loading…" else "Unavailable offline",
                style = PipeBombTheme.typography.trackTitle,
                color = if (isCurrent) colors.accentPrimary else colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.let { DisplayText.artists(it) }.orEmpty(),
                style = PipeBombTheme.typography.metadata,
                color = colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_x),
            contentDescription = "Remove from queue",
            onClick = onRemove,
            size = PipeBombIconButtonSize.Medium,
            tone = PipeBombIconButtonTone.Neutral,
        )
    }
}
