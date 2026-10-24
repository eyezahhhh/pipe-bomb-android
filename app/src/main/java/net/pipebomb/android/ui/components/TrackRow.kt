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
import androidx.compose.foundation.layout.width
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
import net.pipebomb.android.core.model.EphemeralTrack
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
    showArtwork: Boolean = true,
    trackNumber: Int? = null,
) {
    TrackRowContent(
        title = DisplayText.title(track),
        artist = DisplayText.artists(track),
        artwork = DisplayText.artwork(track),
        duration = DisplayText.duration(track),
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        onClick = onClick,
        onPlay = onPlay,
        onMore = onMore,
        modifier = modifier,
        showArtwork = showArtwork,
        trackNumber = trackNumber,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EphemeralTrackRow(
    track: EphemeralTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    trackNumber: Int? = null,
) {
    TrackRowContent(
        title = DisplayText.title(track),
        artist = DisplayText.artists(track),
        artwork = DisplayText.artwork(track),
        duration = DisplayText.duration(track),
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        onClick = onClick,
        onPlay = onPlay,
        onMore = onMore,
        modifier = modifier,
        showArtwork = showArtwork,
        trackNumber = trackNumber,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRowContent(
    title: String,
    artist: String,
    artwork: net.pipebomb.android.core.model.Resource?,
    duration: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
    showArtwork: Boolean,
    trackNumber: Int?,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(if (isCurrent) colors.accentSubtle else colors.bgSurface)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        when {
            showArtwork -> ResourceImage(
                resource = artwork,
                modifier = Modifier
                    .size(42.dp)
                    .clip(PipeBombTheme.shapes.small),
            )

            trackNumber != null -> Text(
                text = trackNumber.toString(),
                style = PipeBombTheme.typography.metadata,
                color = colors.fgTertiary,
                modifier = Modifier.width(24.dp),
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = PipeBombTheme.typography.trackTitle,
                color = if (isCurrent) colors.accentPrimary else colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                Text(
                    text = artist,
                    style = PipeBombTheme.typography.metadata,
                    color = colors.fgSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (duration != null) {
                    Text(
                        text = "· $duration",
                        style = PipeBombTheme.typography.metadata,
                        color = colors.fgTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (isCurrent) {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(
                    if (isPlaying) R.drawable.ic_tabler_pause_filled else R.drawable.ic_tabler_play_filled,
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlay,
                size = PipeBombIconButtonSize.Medium,
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
