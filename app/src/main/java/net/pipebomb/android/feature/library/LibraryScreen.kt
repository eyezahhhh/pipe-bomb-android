package net.pipebomb.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.AlbumRow
import net.pipebomb.android.ui.components.ArtistRow
import net.pipebomb.android.ui.components.PipeBombTabs
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PlaylistRow
import net.pipebomb.android.ui.components.TrackRow

private val tabs = listOf("Tracks", "Albums", "Artists", "Playlists", "History")

@Composable
fun LibraryScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        PipeBombTabs(
            options = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        when (selectedTab) {
            0 -> {
                if (tracks.itemCount == 0 && tracks.loadState.refresh is androidx.paging.LoadState.Loading) {
                    LoadingBox()
                } else if (tracks.itemCount == 0) {
                    EmptyBox("No saved tracks")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        items(
                            count = tracks.itemCount,
                            key = { index -> tracks.peek(index)?.key?.toString() ?: "placeholder-$index" },
                        ) { index ->
                            val track = tracks[index]
                            if (track != null) {
                                TrackRow(
                                    track = track,
                                    isCurrent = track.key.toString() == currentKey,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        onPlay(tracks.itemSnapshotList.items.mapNotNull { it?.key }, index)
                                    },
                                    onPlay = {
                                        onPlay(tracks.itemSnapshotList.items.mapNotNull { it?.key }, index)
                                    },
                                    onMore = { onTrackMore(track) },
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                if (albums.itemCount == 0) {
                    EmptyBox("No saved albums")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        items(
                            count = albums.itemCount,
                            key = { index -> albums.peek(index)?.let { it.uuid ?: it.identityKey } ?: "placeholder-$index" },
                        ) { index ->
                            val album = albums[index]
                            if (album != null) {
                                AlbumRow(
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

            2 -> {
                if (artists.itemCount == 0) {
                    EmptyBox("No saved artists")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        items(
                            count = artists.itemCount,
                            key = { index -> artists.peek(index)?.let { it.uuid ?: it.identityKey } ?: "placeholder-$index" },
                        ) { index ->
                            val artist = artists[index]
                            if (artist != null) {
                                ArtistRow(
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
            }

            3 -> {
                if (playlists.isEmpty()) {
                    EmptyBox("No playlists")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        items(playlists, key = { it.uuid }) { playlist ->
                            PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.uuid) })
                        }
                    }
                }
            }

            else -> {
                if (history.isEmpty()) {
                    EmptyBox("Nothing played yet")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        itemsIndexed(
                            items = history,
                            key = { _, track -> track.key.toString() },
                        ) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = track.key.toString() == currentKey,
                                isPlaying = isPlaying,
                                onClick = { onPlay(history.map { it.key }, index) },
                                onPlay = { onPlay(history.map { it.key }, index) },
                                onMore = { onTrackMore(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        PipeBombSpinner()
    }
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxWidth().fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgTertiary,
        )
    }
}
