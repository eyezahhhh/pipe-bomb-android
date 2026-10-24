package net.pipebomb.android.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun AlbumScreen(
    albumId: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumId) { viewModel.load(albumId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val album = state.album

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipeBombSpinner()
        }

        state.error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = PipeBombTheme.colors.error)
        }

        album != null -> {
            val tracks = album.tracks.orEmpty()
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
                    ) {
                        ResourceImage(
                            resource = DisplayText.artwork(album),
                            modifier = Modifier
                                .size(160.dp)
                                .clip(PipeBombTheme.shapes.large)
                                .background(PipeBombTheme.colors.bgElevated),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                            Text(
                                text = DisplayText.title(album),
                                style = PipeBombTheme.typography.sectionHeading,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            Text(
                                text = DisplayText.artists(album),
                                style = PipeBombTheme.typography.bodySecondary,
                                color = PipeBombTheme.colors.fgSecondary,
                            )
                            val year = AttributeReader.integer(album.attributes, "year")
                            Text(
                                text = listOfNotNull(
                                    year?.toString(),
                                    if (tracks.size == 1) "1 track" else "${tracks.size} tracks",
                                ).joinToString(" · "),
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
                            onClick = { onPlay(tracks.map { it.key }, 0) },
                            enabled = tracks.isNotEmpty(),
                        )
                        PipeBombButton(
                            text = "Shuffle",
                            onClick = { onPlay(tracks.shuffled().map { it.key }, 0) },
                            variant = PipeBombButtonVariant.Secondary,
                            enabled = tracks.isNotEmpty(),
                        )
                        PipeBombButton(
                            text = if (album.bookmarked == true) "Saved" else "Save",
                            onClick = viewModel::toggleSave,
                            variant = PipeBombButtonVariant.Ghost,
                            enabled = album.uuid != null,
                        )
                    }
                }
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.key.toString() },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.key.toString() == currentKey,
                        isPlaying = isPlaying,
                        onClick = { onPlay(tracks.map { it.key }, index) },
                        onPlay = { onPlay(tracks.map { it.key }, index) },
                        showArtwork = false,
                        trackNumber = index + 1,
                        onMore = { onTrackMore(track) },
                    )
                }
            }
        }
    }
}
