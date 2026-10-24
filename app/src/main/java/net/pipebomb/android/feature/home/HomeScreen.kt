package net.pipebomb.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PlaylistCard
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun HomeScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val greeting = greetingForHour()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
    ) {
        item {
            Text(
                text = "$greeting, ${state.username.ifBlank { "listener" }}",
                style = PipeBombTheme.typography.greeting,
                color = PipeBombTheme.colors.fgPrimary,
            )
        }
        if (state.isLoading) {
            item { PipeBombSpinner() }
        }
        if (state.error != null) {
            item {
                Text(
                    text = state.error!!,
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.error,
                )
            }
        }
        if (state.playlists.isNotEmpty()) {
            item {
                Text(
                    text = "Your playlists",
                    style = PipeBombTheme.typography.sectionHeading,
                    color = PipeBombTheme.colors.fgPrimary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                    items(state.playlists, key = { it.uuid }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.uuid) },
                        )
                    }
                }
            }
        }
        if (state.recentTracks.isNotEmpty()) {
            item {
                Text(
                    text = "Recently played",
                    style = PipeBombTheme.typography.sectionHeading,
                    color = PipeBombTheme.colors.fgPrimary,
                )
            }
            itemsIndexed(
                items = state.recentTracks,
                key = { _, track -> track.key.toString() },
            ) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = track.key.toString() == currentKey,
                    isPlaying = isPlaying,
                    onClick = { onPlay(state.recentTracks.map { it.key }, index) },
                    onPlay = { onPlay(state.recentTracks.map { it.key }, index) },
                    onMore = {},
                )
            }
        }
    }
}

private fun greetingForHour(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour in 5..11 -> "Good morning"
        hour in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
