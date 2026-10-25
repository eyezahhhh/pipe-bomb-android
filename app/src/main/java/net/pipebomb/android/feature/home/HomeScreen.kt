package net.pipebomb.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.data.download.DownloadCollectionItem
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.ui.components.AlbumCard
import net.pipebomb.android.ui.components.ArtistCard
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.PlaylistCard
import net.pipebomb.android.ui.components.TrackRow

@Composable
fun HomeScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    onOpenArtist: (String) -> Unit,
    isOnline: Boolean = true,
    downloads: List<DownloadItem> = emptyList(),
    collections: List<DownloadCollectionItem> = emptyList(),
    onOpenDownloads: () -> Unit = {},
    onOpenCollection: (DownloadCollectionItem) -> Unit = {},
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val greeting = greetingForHour()
    var previousOnline by remember { mutableStateOf(isOnline) }
    LaunchedEffect(isOnline) {
        if (!previousOnline && isOnline) viewModel.load()
        previousOnline = isOnline
    }
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.load()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
    ) {
        item {
            HomeHeader(
                greeting = greeting,
                username = state.username.ifBlank { "listener" },
            )
        }
        if (isOnline) {
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = PipeBombTheme.spacing.xxl),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        PipeBombSpinner()
                    }
                }
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
            playlistShelf("My playlists", state.myPlaylists, ownerOverride = state.username) {
                onOpenPlaylist(it.uuid)
            }
            playlistShelf("System playlists", state.systemPlaylists) { onOpenPlaylist(it.uuid) }
            playlistShelf("Friends' playlists", state.friendsPlaylists) { onOpenPlaylist(it.uuid) }
            playlistShelf("Public playlists", state.publicPlaylists) { onOpenPlaylist(it.uuid) }

            if (state.recentAlbums.isNotEmpty()) {
                item { SectionTitle("Recently played") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                        items(state.recentAlbums, key = { it.uuid ?: it.identityKey ?: it.hashCode().toString() }) { album ->
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
            if (state.recentArtists.isNotEmpty()) {
                item { SectionTitle("Recent artists") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
                        items(state.recentArtists, key = { it.uuid ?: it.identityKey ?: it.hashCode().toString() }) { artist ->
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
            if (state.recentTracks.isNotEmpty()) {
                item { SectionTitle("Last played") }
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
                        onMore = { onTrackMore(track) },
                    )
                }
            }
        } else {
            if (collections.isNotEmpty()) {
                item { SectionTitle("Kept playlists & albums") }
                items(collections, key = { it.id }) { collection ->
                    OfflineCollectionRow(
                        collection = collection,
                        onClick = { onOpenCollection(collection) },
                    )
                }
            }
            if (downloads.isNotEmpty()) {
                item { SectionTitle("Recently downloaded") }
                itemsIndexed(
                    items = downloads.take(10),
                    key = { _, item -> item.entity.trackKey },
                ) { index, item ->
                    TrackRow(
                        track = item.track,
                        isCurrent = item.entity.trackKey == currentKey,
                        isPlaying = isPlaying,
                        onClick = { onPlay(downloads.take(10).map { it.key }, index) },
                        onPlay = { onPlay(downloads.take(10).map { it.key }, index) },
                        onMore = {},
                    )
                }
            }
            if (collections.isEmpty() && downloads.isEmpty()) {
                item {
                    Text(
                        text = "No downloads yet. Connect to browse your library.",
                        style = PipeBombTheme.typography.bodySecondary,
                        color = PipeBombTheme.colors.fgTertiary,
                    )
                }
            }
            item {
                PipeBombButton(
                    text = "Open Downloads",
                    onClick = onOpenDownloads,
                    variant = PipeBombButtonVariant.Ghost,
                )
            }
        }
        item { Box(Modifier.padding(bottom = PipeBombTheme.spacing.lg)) }
    }
}

@Composable
private fun OfflineCollectionRow(
    collection: DownloadCollectionItem,
    onClick: () -> Unit,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PipeBombTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = PipeBombTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        ResourceImage(
            resource = collection.artworkUrl?.let { Resource(url = it) },
            modifier = Modifier
                .size(52.dp)
                .clip(PipeBombTheme.shapes.small)
                .background(colors.bgElevated),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = collection.title,
                style = PipeBombTheme.typography.trackTitle,
                color = colors.fgPrimary,
                maxLines = 1,
            )
            Text(
                text = "${collection.type.name} · ${collection.downloadedCount}/${collection.memberCount} downloaded",
                style = PipeBombTheme.typography.metadata,
                color = colors.fgSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    username: String,
) {
    val colors = PipeBombTheme.colors
    Column(
        modifier = Modifier
            .padding(top = PipeBombTheme.spacing.xl, bottom = PipeBombTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.fgPrimary)) { append("$greeting ") }
                withStyle(SpanStyle(color = colors.accentPrimary, fontWeight = FontWeight.Bold)) {
                    append(username)
                }
            },
            style = PipeBombTheme.typography.greeting,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = PipeBombTheme.typography.sectionHeading,
        color = PipeBombTheme.colors.fgPrimary,
        modifier = Modifier.padding(
            top = PipeBombTheme.spacing.xl - PipeBombTheme.spacing.listGap,
        ),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistShelf(
    title: String,
    playlists: List<net.pipebomb.android.core.model.Playlist>,
    ownerOverride: String? = null,
    onOpen: (net.pipebomb.android.core.model.Playlist) -> Unit,
) {
    if (playlists.isEmpty()) return
    item { SectionTitle(title) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md)) {
            items(playlists, key = { it.uuid }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpen(playlist) },
                    ownerOverride = ownerOverride,
                )
            }
        }
    }
}

private fun greetingForHour(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour in 5..11 -> "Good morning,"
        hour in 12..17 -> "Good afternoon,"
        else -> "Good evening,"
    }
}
