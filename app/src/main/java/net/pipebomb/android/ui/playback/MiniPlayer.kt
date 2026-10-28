package net.pipebomb.android.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonStyle
import net.pipebomb.android.ui.components.ResourceImage

@Composable
fun MiniPlayer(
    queue: List<TrackKey>,
    currentIndex: Int,
    tracksByKey: Map<TrackKey, Track>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onEnsureTracks: (Iterable<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = PipeBombTheme.spacing.sm,
                vertical = PipeBombTheme.spacing.xs,
            ),
    ) {
        MiniPlayerCard(
            queue = queue,
            currentIndex = currentIndex,
            tracksByKey = tracksByKey,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onOpen = onOpen,
            onToggle = onToggle,
            onSelectIndex = onSelectIndex,
            onEnsureTracks = onEnsureTracks,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
    }
}

@Composable
fun MiniPlayerCard(
    queue: List<TrackKey>,
    currentIndex: Int,
    tracksByKey: Map<TrackKey, Track>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onEnsureTracks: (Iterable<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = queue.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    TrackPagerEffects(
        pagerState = pagerState,
        currentIndex = currentIndex,
        itemCount = queue.size,
        onEnsureTracks = onEnsureTracks,
        onSelectIndex = onSelectIndex,
    )

    Row(
        modifier = modifier
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .testTag("mini-player")
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        if (queue.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "Nothing playing",
                    style = PipeBombTheme.typography.bodySecondary,
                    color = PipeBombTheme.colors.fgTertiary,
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("mini-player-pager"),
                beyondViewportPageCount = 1,
            ) { page ->
                val pageTrack = tracksByKey[queue[page]]
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
                ) {
                    ResourceImage(
                        resource = pageTrack?.let { DisplayText.artwork(it) },
                        modifier = Modifier.size(48.dp).clip(PipeBombTheme.shapes.small),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = pageTrack?.let { DisplayText.title(it) } ?: "Loading…",
                            style = PipeBombTheme.typography.trackTitle,
                            color = if (pageTrack == null) {
                                PipeBombTheme.colors.fgTertiary
                            } else {
                                PipeBombTheme.colors.fgPrimary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pageTrack?.let { DisplayText.artists(it) }.orEmpty(),
                            style = PipeBombTheme.typography.metadata,
                            color = PipeBombTheme.colors.fgSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
