package net.pipebomb.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.playback.PlaybackUiState
import net.pipebomb.android.playback.RepeatMode
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonStyle
import net.pipebomb.android.ui.components.PipeBombProgressTrack
import net.pipebomb.android.ui.components.ResourceImage

@Composable
fun NowPlayingScreen(
    track: Track?,
    state: PlaybackUiState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PipeBombTheme.colors.bgBase)
            .padding(PipeBombTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
    ) {
        if (track != null) {
            ResourceImage(
                resource = DisplayText.artwork(track),
                modifier = Modifier
                    .size(300.dp)
                    .clip(PipeBombTheme.shapes.large),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DisplayText.title(track),
                    style = PipeBombTheme.typography.sectionHeading,
                    color = PipeBombTheme.colors.fgPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = DisplayText.artists(track),
                    style = PipeBombTheme.typography.bodySecondary,
                    color = PipeBombTheme.colors.fgSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = "Nothing playing",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgTertiary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
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
                    color = PipeBombTheme.colors.fgTertiary,
                )
                Text(
                    text = if (state.durationMs > 0) formatTime(state.durationMs / 1000f) else "-:-",
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.fgTertiary,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
        ) {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_shuffle),
                contentDescription = "Shuffle",
                onClick = onShuffle,
                tone = net.pipebomb.android.ui.components.PipeBombIconButtonTone.Neutral,
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
                    net.pipebomb.android.ui.components.PipeBombIconButtonTone.Neutral
                } else {
                    net.pipebomb.android.ui.components.PipeBombIconButtonTone.Primary
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_list),
                contentDescription = "Queue",
                onClick = onOpenQueue,
                size = PipeBombIconButtonSize.Medium,
                tone = net.pipebomb.android.ui.components.PipeBombIconButtonTone.Neutral,
            )
        }

        if (state.error != null) {
            Text(
                text = state.error!!,
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.error,
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
