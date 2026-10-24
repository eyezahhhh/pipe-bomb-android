package net.pipebomb.android.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.PlaylistVisibility
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.TrackRow
import net.pipebomb.android.ui.components.TrackChunkErrorRow
import net.pipebomb.android.ui.components.TrackSkeletonRow

@Composable
fun PlaylistScreen(
    playlistUuid: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistUuid) { viewModel.load(playlistUuid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist
    val scope = rememberCoroutineScope()

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipeBombSpinner()
        }

        state.error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = PipeBombTheme.colors.error)
        }

        playlist != null -> {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(horizontal = PipeBombTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(top = PipeBombTheme.spacing.sm),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
                    ) {
                        ResourceImage(
                            resource = DisplayText.artwork(playlist),
                            modifier = Modifier
                                .size(128.dp)
                                .clip(PipeBombTheme.shapes.large)
                                .background(PipeBombTheme.colors.bgElevated),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                            Text(
                                text = DisplayText.title(playlist),
                                style = PipeBombTheme.typography.sectionHeading,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            DisplayText.ownerName(playlist)?.let { owner ->
                                Text(
                                    text = owner,
                                    style = PipeBombTheme.typography.bodySecondary,
                                    color = PipeBombTheme.colors.fgSecondary,
                                )
                            }
                            Text(
                                text = buildString {
                                    append(
                                        when (playlist.visibility) {
                                            PlaylistVisibility.Public -> "Public"
                                            PlaylistVisibility.Unlisted -> "Unlisted"
                                            PlaylistVisibility.Private -> "Private"
                                        },
                                    )
                                    append(" · ")
                                    append(
                                        if (state.totalCount == 1) {
                                            "1 track"
                                        } else {
                                            "${state.totalCount} tracks"
                                        },
                                    )
                                },
                                style = PipeBombTheme.typography.metadata,
                                color = PipeBombTheme.colors.fgTertiary,
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
                        PipeBombButton(
                            text = "Play",
                            onClick = {
                                scope.launch {
                                    val all = viewModel.loadAllTracks()
                                    if (all.isNotEmpty()) onPlay(all.map { it.key }, 0)
                                }
                            },
                            enabled = state.totalCount > 0,
                        )
                        PipeBombButton(
                            text = "Shuffle",
                            onClick = {
                                scope.launch {
                                    val all = viewModel.loadAllTracks().shuffled()
                                    if (all.isNotEmpty()) onPlay(all.map { it.key }, 0)
                                }
                            },
                            variant = PipeBombButtonVariant.Secondary,
                            enabled = state.totalCount > 0,
                        )
                    }
                }
                items(
                    count = state.totalCount,
                    key = { index -> index },
                ) { index ->
                    val track = state.tracksByIndex[index]
                    if (track != null) {
                        TrackRow(
                            track = track,
                            isCurrent = track.key.toString() == currentKey,
                            isPlaying = isPlaying,
                            onClick = {
                                scope.launch {
                                    val all = viewModel.loadAllTracks()
                                    if (all.isNotEmpty()) {
                                        onPlay(all.map { it.key }, index.coerceIn(0, all.lastIndex))
                                    }
                                }
                            },
                            onPlay = {},
                            onMore = { onTrackMore(track) },
                        )
                    } else {
                        val chunkIndex = index / PLAYLIST_CHUNK_SIZE
                        LaunchedEffect(chunkIndex) { viewModel.ensureChunk(chunkIndex) }
                        val chunkError = state.chunkErrors[chunkIndex]
                        if (chunkError != null && index % PLAYLIST_CHUNK_SIZE == 0) {
                            TrackChunkErrorRow(onRetry = { viewModel.ensureChunk(chunkIndex) })
                        } else {
                            TrackSkeletonRow()
                        }
                    }
                }
                item { Box(Modifier.padding(bottom = PipeBombTheme.spacing.sm)) }
            }
        }
    }
}
