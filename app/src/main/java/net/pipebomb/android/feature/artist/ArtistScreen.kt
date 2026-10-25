package net.pipebomb.android.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import net.pipebomb.android.ui.components.AlbumCard
import net.pipebomb.android.ui.components.MediaActions
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun ArtistScreen(
    artistId: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val artist = state.artist

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipeBombSpinner()
        }

        state.error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = PipeBombTheme.colors.error)
        }

        artist != null -> {
            val tracks = artist.tracks.orEmpty()
            val albums = artist.albums.orEmpty()
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
                    ) {
                        ResourceImage(
                            resource = DisplayText.artwork(artist),
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(PipeBombTheme.colors.bgElevated),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                            Text(
                                text = DisplayText.title(artist),
                                style = PipeBombTheme.typography.sectionHeading,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            val genres = AttributeReader.strings(artist.attributes, "genres", multiple = true)
                                ?: AttributeReader.strings(artist.attributes, "genre", multiple = true)
                            if (!genres.isNullOrEmpty()) {
                                Text(
                                    text = genres.joinToString(" · "),
                                    style = PipeBombTheme.typography.metadata,
                                    color = PipeBombTheme.colors.fgTertiary,
                                )
                            }
                            MediaActions(
                                onPlay = { onPlay(tracks.map { it.key }, 0) },
                                isSaved = artist.bookmarked == true,
                                onToggleSave = viewModel::toggleSave,
                                enabled = tracks.isNotEmpty(),
                                modifier = Modifier.padding(top = PipeBombTheme.spacing.xs),
                            )
                        }
                    }
                }
                if (tracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top tracks",
                            style = PipeBombTheme.typography.sectionHeading,
                            color = PipeBombTheme.colors.fgPrimary,
                            modifier = Modifier.padding(
                                top = PipeBombTheme.spacing.xl - PipeBombTheme.spacing.listGap,
                            ),
                        )
                    }
                    itemsIndexed(tracks, key = { _, track -> track.key.toString() }) { index, track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.key.toString() == currentKey,
                            isPlaying = isPlaying,
                            onClick = { onPlay(tracks.map { it.key }, index) },
                            onPlay = { onPlay(tracks.map { it.key }, index) },
                            onMore = { onTrackMore(track) },
                        )
                    }
                }
                if (albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            style = PipeBombTheme.typography.sectionHeading,
                            color = PipeBombTheme.colors.fgPrimary,
                            modifier = Modifier.padding(
                                top = PipeBombTheme.spacing.xl - PipeBombTheme.spacing.listGap,
                            ),
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                            items(albums, key = { it.uuid ?: it.identityKey ?: it.hashCode().toString() }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = {
                                        val id = album.uuid ?: album.identityKey
                                        if (id != null) onOpenAlbum(id)
                                    },
                                )
                            }
                        }
                    }
                }
                item { Box(Modifier.padding(bottom = PipeBombTheme.spacing.sm)) }
            }
        }
    }
}
