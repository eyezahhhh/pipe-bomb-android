package net.pipebomb.android.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.EphemeralSearchResults
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.data.download.DownloadCollectionItem
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.ui.components.AlbumCard
import net.pipebomb.android.ui.components.ArtistCard
import net.pipebomb.android.ui.components.EphemeralTrackRow
import net.pipebomb.android.ui.components.PipeBombChip
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PipeBombTextField
import net.pipebomb.android.ui.components.TrackRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    isOnline: Boolean = true,
    downloads: List<DownloadItem> = emptyList(),
    collections: List<DownloadCollectionItem> = emptyList(),
    onOpenCollection: (DownloadCollectionItem) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var previousOnline by remember { mutableStateOf(isOnline) }
    LaunchedEffect(isOnline) {
        if (!previousOnline && isOnline && state.query.isNotBlank()) {
            viewModel.setQuery(state.query)
        }
        previousOnline = isOnline
    }
    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        PipeBombTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = "Search tracks, albums, artists",
        )

        if (isOnline) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            PipeBombChip(
                text = "Library",
                selected = state.selectedSource == null,
                onClick = { viewModel.selectSource(null) },
            )
            state.sources.forEach { source ->
                PipeBombChip(
                    text = source.name,
                    selected = state.selectedSource == source,
                    onClick = { viewModel.selectSource(source) },
                )
            }
        }

        when {
            !isOnline -> OfflineDownloadsSearch(
                downloads = downloads,
                collections = collections,
                query = state.query,
                currentKey = currentKey,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onOpenCollection = onOpenCollection,
            )

            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PipeBombSpinner()
            }

            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.error!!,
                    style = PipeBombTheme.typography.bodySecondary,
                    color = PipeBombTheme.colors.error,
                    textAlign = TextAlign.Center,
                )
            }

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
                    EmptyState("No results")
                } else {
                    SearchResultsList(
                        results = results,
                        currentKey = currentKey,
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        onTrackMore = onTrackMore,
                    )
                }
            }

            state.query.isBlank() -> EmptyState("Search your library or an external source")
        }
    }
}

@Composable
private fun OfflineDownloadsSearch(
    downloads: List<DownloadItem>,
    collections: List<DownloadCollectionItem>,
    query: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenCollection: (DownloadCollectionItem) -> Unit,
) {
    val needle = query.trim()
    val filtered = downloads.filter { item ->
        needle.isEmpty() ||
            item.entity.title.contains(needle, ignoreCase = true) ||
            item.entity.artist?.contains(needle, ignoreCase = true) == true
    }
    val filteredCollections = collections.filter { collection ->
        needle.isEmpty() || collection.title.contains(needle, ignoreCase = true)
    }
    if (filtered.isEmpty() && filteredCollections.isEmpty()) {
        EmptyState(
            if (needle.isEmpty()) "No downloads yet" else "No downloaded items match \"$needle\"",
        )
        return
    }
    val keys = filtered.map { it.key }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
    ) {
        if (filteredCollections.isNotEmpty()) {
            item {
                Text(
                    text = "Kept playlists & albums",
                    style = PipeBombTheme.typography.sectionHeading,
                    color = PipeBombTheme.colors.fgPrimary,
                )
            }
            items(filteredCollections, key = { it.id }) { collection ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCollection(collection) }
                        .padding(vertical = PipeBombTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = collection.title,
                        style = PipeBombTheme.typography.trackTitle,
                        color = PipeBombTheme.colors.fgPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = "${collection.type.name} · ${collection.downloadedCount}/${collection.memberCount} downloaded",
                        style = PipeBombTheme.typography.metadata,
                        color = PipeBombTheme.colors.fgSecondary,
                    )
                }
            }
        }
        if (filtered.isNotEmpty()) {
            item {
                Text(
                    text = "Downloads",
                    style = PipeBombTheme.typography.sectionHeading,
                    color = PipeBombTheme.colors.fgPrimary,
                )
            }
            itemsIndexed(filtered, key = { _, item -> item.entity.trackKey }) { index, item ->
                TrackRow(
                    track = item.track,
                    isCurrent = item.entity.trackKey == currentKey,
                    isPlaying = isPlaying,
                    onClick = { onPlay(keys, index) },
                    onPlay = { onPlay(keys, index) },
                    onMore = {},
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgTertiary,
            textAlign = TextAlign.Center,
        )
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
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit,
) {
    val topTracks = if (results.albums.isNotEmpty() || results.artists.isNotEmpty()) {
        results.tracks.take(5)
    } else {
        results.tracks
    }
    val remainingTracks = results.tracks.drop(topTracks.size)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
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
                    onMore = { onTrackMore(track) },
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
                    onMore = { onTrackMore(track) },
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
    ) {
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
