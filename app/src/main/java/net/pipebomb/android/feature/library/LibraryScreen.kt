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
import androidx.paging.compose.collectAsLazyPagingItems
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.AlbumRow
import net.pipebomb.android.ui.components.ArtistRow
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PipeBombTabs
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun LibraryScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        PipeBombTabs(
            options = listOf("Tracks", "Albums", "Artists"),
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
                        itemsIndexed(
                            items = List(tracks.itemCount) { tracks[it] },
                            key = { index, track -> track?.key?.toString() ?: index.toString() },
                        ) { index, track ->
                            if (track != null) {
                                TrackRow(
                                    track = track,
                                    isCurrent = track.key.toString() == currentKey,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        val keys = (0 until tracks.itemCount).mapNotNull {
                                            tracks[it]?.key
                                        }
                                        onPlay(keys, index)
                                    },
                                    onPlay = {
                                        val keys = (0 until tracks.itemCount).mapNotNull {
                                            tracks[it]?.key
                                        }
                                        onPlay(keys, index)
                                    },
                                    onMore = {},
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
                            items = List(albums.itemCount) { albums[it] },
                            key = { it?.uuid ?: it.hashCode().toString() },
                        ) { album ->
                            album?.let {
                                AlbumRow(
                                    album = it,
                                    onClick = {
                                        val id = it.uuid ?: it.identityKey
                                        if (id != null) onOpenAlbum(id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                if (artists.itemCount == 0) {
                    EmptyBox("No saved artists")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        items(
                            items = List(artists.itemCount) { artists[it] },
                            key = { it?.uuid ?: it.hashCode().toString() },
                        ) { artist ->
                            artist?.let {
                                ArtistRow(
                                    artist = it,
                                    onClick = {
                                        val id = it.uuid ?: it.identityKey
                                        if (id != null) onOpenArtist(id)
                                    },
                                )
                            }
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
    Text(
        text = text,
        style = PipeBombTheme.typography.bodySecondary,
        color = PipeBombTheme.colors.fgTertiary,
        modifier = Modifier.padding(top = 32.dp),
    )
}
