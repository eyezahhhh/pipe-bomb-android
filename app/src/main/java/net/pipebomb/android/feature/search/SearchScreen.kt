package net.pipebomb.android.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.EphemeralSearchResults
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.AlbumCard
import net.pipebomb.android.ui.components.ArtistCard
import net.pipebomb.android.ui.components.EphemeralTrackRow
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PipeBombTextField
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun SearchScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        PipeBombTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = "Search tracks, albums, artists",
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
            item {
                PipeBombButton(
                    text = "Library",
                    onClick = { viewModel.selectSource(null) },
                    variant = if (state.selectedSource == null) {
                        PipeBombButtonVariant.Primary
                    } else {
                        PipeBombButtonVariant.Secondary
                    },
                )
            }
            items(state.sources, key = { "${it.pluginId}:${it.id}" }) { source ->
                PipeBombButton(
                    text = source.name,
                    onClick = { viewModel.selectSource(source) },
                    variant = if (state.selectedSource == source) {
                        PipeBombButtonVariant.Primary
                    } else {
                        PipeBombButtonVariant.Secondary
                    },
                )
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                PipeBombSpinner()
            }

            state.error != null -> Text(
                text = state.error!!,
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.error,
            )

            state.selectedSource != null && state.ephemeralResults != null -> {
                EphemeralResultsList(
                    results = state.ephemeralResults!!,
                    source = state.selectedSource!!,
                    currentKey = currentKey,
                    isPlaying = isPlaying,
                    onPlay = onPlay,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                )
            }

            state.selectedSource == null && state.results != null -> {
                val results = state.results!!
                if (results.tracks.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()) {
                    Text(
                        text = "No results",
                        style = PipeBombTheme.typography.bodySecondary,
                        color = PipeBombTheme.colors.fgTertiary,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                } else {
                    SearchResultsList(
                        results = results,
                        currentKey = currentKey,
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                    )
                }
            }

            state.query.isBlank() -> Text(
                text = "Search your library or an external source",
                style = PipeBombTheme.typography.bodySecondary,
                color = PipeBombTheme.colors.fgTertiary,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: net.pipebomb.android.core.model.SearchResults,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val topTracks = if (results.albums.isNotEmpty() || results.artists.isNotEmpty()) {
        results.tracks.take(5)
    } else {
        results.tracks
    }
    val remainingTracks = results.tracks.drop(topTracks.size)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        if (topTracks.isNotEmpty()) {
            item { SectionTitle("Top tracks") }
            itemsIndexed(
                items = topTracks,
                key = { _, track -> track.key.toString() },
            ) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = track.key.toString() == currentKey,
                    isPlaying = isPlaying,
                    onClick = { onPlay(results.tracks.map { it.key }, index) },
                    onPlay = { onPlay(results.tracks.map { it.key }, index) },
                    onMore = {},
                )
            }
        }
        if (results.artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                    items(results.artists, key = { it.uuid ?: it.identityKey ?: it.hashCode().toString() }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = {
                                val id = artist.uuid ?: artist.identityKey
                                if (id != null) onOpenArtist(id)
                            },
                        )
                    }
                }
            }
        }
        if (results.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                    items(results.albums, key = { it.uuid ?: it.identityKey ?: it.hashCode().toString() }) { album ->
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
        if (remainingTracks.isNotEmpty()) {
            item { SectionTitle("Tracks") }
            itemsIndexed(
                items = remainingTracks,
                key = { _, track -> track.key.toString() },
            ) { index, track ->
                val actualIndex = topTracks.size + index
                TrackRow(
                    track = track,
                    isCurrent = track.key.toString() == currentKey,
                    isPlaying = isPlaying,
                    onClick = { onPlay(results.tracks.map { it.key }, actualIndex) },
                    onPlay = { onPlay(results.tracks.map { it.key }, actualIndex) },
                    onMore = {},
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = PipeBombTheme.typography.sectionHeading,
        color = PipeBombTheme.colors.fgPrimary,
        modifier = Modifier.padding(top = PipeBombTheme.spacing.sm),
    )
}

@Composable
private fun EphemeralResultsList(
    results: EphemeralSearchResults,
    source: EphemeralSource,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val keys = results.tracks.map { it.key }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
        if (results.tracks.isNotEmpty()) {
            item { SectionTitle("Tracks from ${source.name}") }
            itemsIndexed(results.tracks, key = { _, track -> track.key.toString() }) { index, track ->
                EphemeralTrackRow(
                    track = track,
                    isCurrent = track.key.toString() == currentKey,
                    isPlaying = isPlaying,
                    onClick = { onPlay(keys, index) },
                    onPlay = { onPlay(keys, index) },
                    onMore = {},
                )
            }
        }
        if (results.artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                    items(results.artists) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = {
                                val id = artist.uuid ?: artist.identityKey
                                if (id != null) onOpenArtist(id)
                            },
                        )
                    }
                }
            }
        }
        if (results.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                    items(results.albums) { album ->
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
    }
}
