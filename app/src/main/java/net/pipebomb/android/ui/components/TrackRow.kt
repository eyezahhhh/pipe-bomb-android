package net.pipebomb.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(if (isCurrent) colors.accentSubtle else colors.bgSurface)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        PipeBombIconButton(
            icon = ImageVector.vectorResource(
                if (isCurrent && isPlaying) R.drawable.ic_tabler_pause_filled else R.drawable.ic_tabler_play_filled,
            ),
            contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
            onClick = onPlay,
            style = if (isCurrent) PipeBombIconButtonStyle.Simple else PipeBombIconButtonStyle.Background,
            size = PipeBombIconButtonSize.Medium,
        )
        ResourceImage(
            resource = DisplayText.artwork(track),
            modifier = Modifier
                .size(44.dp)
                .clip(PipeBombTheme.shapes.small),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = DisplayText.title(track),
                style = PipeBombTheme.typography.trackTitle,
                color = if (isCurrent) colors.accentPrimary else colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DisplayText.artists(track),
                style = PipeBombTheme.typography.metadata,
                color = colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_dots),
                contentDescription = "More",
                onClick = onMore,
                size = PipeBombIconButtonSize.Medium,
                tone = PipeBombIconButtonTone.Neutral,
            )
        }
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    currentKey: String?,
    isPlaying: Boolean,
    onTrackClick: (Int) -> Unit,
    onTrackPlay: (Int) -> Unit,
    onTrackMore: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.key.toString() },
        ) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.key.toString() == currentKey,
                isPlaying = isPlaying,
                onClick = { onTrackClick(index) },
                onPlay = { onTrackPlay(index) },
                onMore = { onTrackMore(track) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EphemeralTrackRow(
    track: net.pipebomb.android.core.model.EphemeralTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(if (isCurrent) colors.accentSubtle else colors.bgSurface)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        PipeBombIconButton(
            icon = ImageVector.vectorResource(
                if (isCurrent && isPlaying) R.drawable.ic_tabler_pause_filled else R.drawable.ic_tabler_play_filled,
            ),
            contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
            onClick = onPlay,
            style = if (isCurrent) PipeBombIconButtonStyle.Simple else PipeBombIconButtonStyle.Background,
        )
        ResourceImage(
            resource = DisplayText.artwork(track),
            modifier = Modifier.size(44.dp).clip(PipeBombTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = DisplayText.title(track),
                style = PipeBombTheme.typography.trackTitle,
                color = if (isCurrent) colors.accentPrimary else colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DisplayText.artists(track),
                style = PipeBombTheme.typography.metadata,
                color = colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_dots),
            contentDescription = "More",
            onClick = onMore,
            size = PipeBombIconButtonSize.Medium,
            tone = PipeBombIconButtonTone.Neutral,
        )
    }
}
