package net.pipebomb.android.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun PlaylistScreen(
    playlistUuid: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistUuid) { viewModel.load(playlistUuid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipeBombSpinner()
        }

        state.error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = PipeBombTheme.colors.error)
        }

        playlist != null -> {
            val tracks = playlist.tracks.orEmpty().map { it.track }
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
                    ) {
                        ResourceImage(
                            resource = DisplayText.artwork(playlist),
                            modifier = Modifier
                                .size(160.dp)
                                .clip(PipeBombTheme.shapes.large)
                                .background(PipeBombTheme.colors.bgElevated),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                            Text(
                                text = DisplayText.title(playlist),
                                style = PipeBombTheme.typography.sectionHeading,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            Text(
                                text = playlist.owner.user?.username
                                    ?: playlist.owner.botName
                                    ?: "Pipe Bomb",
                                style = PipeBombTheme.typography.bodySecondary,
                                color = PipeBombTheme.colors.fgSecondary,
                            )
                            Text(
                                text = "${playlist.trackCount ?: tracks.size} tracks",
                                style = PipeBombTheme.typography.metadata,
                                color = PipeBombTheme.colors.fgTertiary,
                            )
                            PipeBombButton(
                                text = "Play",
                                onClick = { onPlay(tracks.map { it.key }, 0) },
                                enabled = tracks.isNotEmpty(),
                            )
                        }
                    }
                }
                itemsIndexed(tracks, key = { _, track -> track.key.toString() }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.key.toString() == currentKey,
                        isPlaying = isPlaying,
                        onClick = { onPlay(tracks.map { it.key }, index) },
                        onPlay = { onPlay(tracks.map { it.key }, index) },
                        onMore = {},
                    )
                }
            }
        }
    }
}
