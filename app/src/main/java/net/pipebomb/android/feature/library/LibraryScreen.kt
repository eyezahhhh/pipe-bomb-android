package net.pipebomb.android.feature.library

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.data.download.DownloadCollectionItem
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.ui.components.AlbumRow
import net.pipebomb.android.ui.components.ArtistRow
import net.pipebomb.android.ui.components.DownloadRow
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonTone
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PipeBombTabs
import net.pipebomb.android.ui.components.PlaylistRow
import net.pipebomb.android.ui.components.TrackChunkErrorRow
import net.pipebomb.android.ui.components.TrackRow
import net.pipebomb.android.ui.components.TrackSkeletonRow

private val mainTabs = listOf("Saved", "Playlists", "History", "Downloads")
private val savedTabs = listOf("Tracks", "Albums", "Artists")

@Composable
fun LibraryScreen(
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    downloads: List<DownloadItem>,
    storageAvailable: Boolean,
    reservePaused: Boolean,
    mobileDataPaused: Boolean,
    onRetryDownload: (DownloadItem) -> Unit,
    onRemoveDownload: (DownloadItem) -> Unit,
    onResumeDownloads: () -> Unit,
    onPlayDownload: (DownloadItem) -> Unit,
    collections: List<DownloadCollectionItem>,
    isOnline: Boolean,
    downloadsRequestKey: Int,
    accountUuid: String?,
    accountName: String?,
    onOpenCollection: (DownloadCollectionItem) -> Unit,
    onStopKeepingCollection: (DownloadCollectionItem) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedSavedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(downloadsRequestKey) {
        if (downloadsRequestKey > 0) selectedTab = 3
    }
    LaunchedEffect(isOnline) {
        if (!isOnline) selectedTab = 3
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) viewModel.ensureHistoryLoaded()
    }
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refreshPlaylists()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        PipeBombTabs(
            options = mainTabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        if (!isOnline && selectedTab != 3) {
            OfflineLibraryPlaceholder(onOpenDownloads = { selectedTab = 3 })
        } else when (selectedTab) {
            0 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
                ) {
                    Text(
                        text = "Tracks, albums, and artists you've saved.",
                        style = PipeBombTheme.typography.metadata,
                        color = PipeBombTheme.colors.fgSecondary,
                        modifier = Modifier.padding(horizontal = PipeBombTheme.spacing.xs),
                    )
                    PipeBombTabs(
                        options = savedTabs,
                        selectedIndex = selectedSavedTab,
                        onSelect = { selectedSavedTab = it },
                    )
                    Box(Modifier.fillMaxSize()) {
                        when (selectedSavedTab) {
                            0 -> SavedTracksTab(
                                tracks = tracks,
                                currentKey = currentKey,
                                isPlaying = isPlaying,
                                onPlay = onPlay,
                                onTrackMore = onTrackMore,
                            )

                            1 -> SavedAlbumsTab(
                                albums = albums,
                                onOpenAlbum = onOpenAlbum,
                            )

                            else -> SavedArtistsTab(
                                artists = artists,
                                onOpenArtist = onOpenArtist,
                            )
                        }
                    }
                }
            }

            1 -> {
                if (playlists.isEmpty()) {
                    EmptyBox("No playlists yet")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                        items(playlists, key = { it.uuid }) { playlist ->
                            PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.uuid) })
                        }
                    }
                }
            }

            2 -> HistoryTab(
                history = history,
                currentKey = currentKey,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onTrackMore = onTrackMore,
                onRetry = viewModel::retryHistory,
                onEnsureChunk = viewModel::ensureHistoryChunk,
            )

            else -> DownloadsTab(
                downloads = downloads,
                collections = collections,
                storageAvailable = storageAvailable,
                reservePaused = reservePaused,
                mobileDataPaused = mobileDataPaused,
                isOnline = isOnline,
                accountUuid = accountUuid,
                accountName = accountName,
                onRetry = onRetryDownload,
                onRemove = onRemoveDownload,
                onResume = onResumeDownloads,
                onPlay = onPlayDownload,
                onOpenCollection = onOpenCollection,
                onStopKeeping = onStopKeepingCollection,
            )
        }
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<DownloadItem>,
    collections: List<DownloadCollectionItem>,
    storageAvailable: Boolean,
    reservePaused: Boolean,
    mobileDataPaused: Boolean,
    isOnline: Boolean,
    accountUuid: String?,
    accountName: String?,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onResume: () -> Unit,
    onPlay: (DownloadItem) -> Unit,
    onOpenCollection: (DownloadCollectionItem) -> Unit,
    onStopKeeping: (DownloadCollectionItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        if (!storageAvailable) {
            Text(
                text = "Download storage is unavailable. Reconnect it in Settings.",
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.error,
                modifier = Modifier.padding(horizontal = PipeBombTheme.spacing.xs),
            )
        }
        if (reservePaused) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Paused for storage reserve",
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.warning,
                )
                PipeBombButton(
                    text = "Resume",
                    onClick = onResume,
                    variant = PipeBombButtonVariant.Ghost,
                )
            }
        }
        if (mobileDataPaused) {
            Text(
                text = "Downloads are waiting for Wi-Fi. Allow mobile data downloads in Settings to continue.",
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.warning,
                modifier = Modifier.padding(horizontal = PipeBombTheme.spacing.xs),
            )
        }
        if (collections.isEmpty() && downloads.isEmpty()) {
            EmptyBox("No downloads yet")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
            ) {
                if (collections.isNotEmpty()) {
                    item { CollectionSectionTitle("Kept playlists & albums") }
                    items(collections, key = { it.id }) { collection ->
                        DownloadCollectionRow(
                            collection = collection,
                            isOnline = isOnline,
                            accountUuid = accountUuid,
                            accountName = accountName,
                            onOpen = { onOpenCollection(collection) },
                            onStopKeeping = { onStopKeeping(collection) },
                        )
                    }
                }
                if (downloads.isNotEmpty()) {
                    val totalBytes = downloads.sumOf { it.entity.bytesDownloaded }
                    item {
                        CollectionSectionTitle(
                            "${downloads.size} ${if (downloads.size == 1) "track" else "tracks"} · " +
                                formatBytes(totalBytes),
                        )
                    }
                    items(downloads, key = { it.entity.trackKey }) { item ->
                        DownloadRow(
                            item = item,
                            onPlay = { onPlay(item) },
                            onRetry = { onRetry(item) },
                            onRemove = { onRemove(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionSectionTitle(text: String) {
    Text(
        text = text,
        style = PipeBombTheme.typography.metadata,
        color = PipeBombTheme.colors.fgSecondary,
        modifier = Modifier.padding(horizontal = PipeBombTheme.spacing.xs),
    )
}

@Composable
private fun DownloadCollectionRow(
    collection: DownloadCollectionItem,
    isOnline: Boolean,
    accountUuid: String?,
    accountName: String?,
    onOpen: () -> Unit,
    onStopKeeping: () -> Unit,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PipeBombTheme.shapes.small)
            .clickable(onClick = onOpen)
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
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${collection.type.name} · ${collection.downloadedCount}/${collection.memberCount} downloaded",
                style = PipeBombTheme.typography.metadata,
                color = colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = collectionStatus(
                    collection = collection,
                    isOnline = isOnline,
                    accountUuid = accountUuid,
                    accountName = accountName,
                ),
                style = PipeBombTheme.typography.metadata,
                color = when {
                    collection.entity.lastError != null -> colors.warning
                    !isOnline -> colors.fgTertiary
                    collection.entity.accountUuid != accountUuid -> colors.warning
                    else -> colors.fgTertiary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_x),
            contentDescription = "Stop keeping downloaded",
            onClick = onStopKeeping,
            tone = PipeBombIconButtonTone.Neutral,
        )
    }
}

private fun collectionStatus(
    collection: DownloadCollectionItem,
    isOnline: Boolean,
    accountUuid: String?,
    accountName: String?,
): String {
    val entity = collection.entity
    if (entity.accountUuid != null && entity.accountUuid != accountUuid) {
        return "Sign in as ${entity.accountName ?: "the owning account"} to update"
    }
    if (!isOnline) return "Waiting for connection"
    entity.lastError?.let { return "Couldn't sync · $it" }
    val syncedAt = entity.lastSyncedAt
    return if (syncedAt == null) {
        "Preparing downloads"
    } else {
        "Updated ${relativeTime(syncedAt)}"
    }
}

private fun relativeTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1_440}d ago"
    }
}

@Composable
private fun OfflineLibraryPlaceholder(onOpenDownloads: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
        ) {
            Text(
                text = "Not available offline",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgSecondary,
            )
            Text(
                text = "Your downloaded music is still available.",
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.fgTertiary,
            )
            PipeBombButton(
                text = "Open Downloads",
                onClick = onOpenDownloads,
                variant = PipeBombButtonVariant.Ghost,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    return String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

@Composable
private fun SavedTracksTab(
    tracks: androidx.paging.compose.LazyPagingItems<net.pipebomb.android.core.model.Track>,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit,
) {
    when {
        tracks.itemCount == 0 && tracks.loadState.refresh is androidx.paging.LoadState.Loading -> LoadingBox()
        tracks.itemCount == 0 -> EmptyBox("No saved tracks yet")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
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
                        onPlay = {},
                        onMore = { onTrackMore(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedAlbumsTab(
    albums: androidx.paging.compose.LazyPagingItems<net.pipebomb.android.core.model.Album>,
    onOpenAlbum: (String) -> Unit,
) {
    when {
        albums.itemCount == 0 && albums.loadState.refresh is androidx.paging.LoadState.Loading -> LoadingBox()
        albums.itemCount == 0 -> EmptyBox("No saved albums yet")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
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

@Composable
private fun SavedArtistsTab(
    artists: androidx.paging.compose.LazyPagingItems<net.pipebomb.android.core.model.Artist>,
    onOpenArtist: (String) -> Unit,
) {
    when {
        artists.itemCount == 0 && artists.loadState.refresh is androidx.paging.LoadState.Loading -> LoadingBox()
        artists.itemCount == 0 -> EmptyBox("No saved artists yet")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
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

@Composable
private fun HistoryTab(
    history: HistoryUiState,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit,
    onRetry: () -> Unit,
    onEnsureChunk: (Int) -> Unit,
) {
    when {
        history.isLoading -> LoadingBox()
        history.error != null -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = history.error!!,
                style = PipeBombTheme.typography.bodySecondary,
                color = PipeBombTheme.colors.error,
            )
            PipeBombButton(
                text = "Retry",
                onClick = onRetry,
                variant = PipeBombButtonVariant.Ghost,
            )
        }

        history.total == 0 -> EmptyBox("Nothing played yet")
        else -> Column(Modifier.fillMaxSize()) {
            Text(
                text = if (history.total == 1) "1 play" else "${history.total} plays",
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.fgSecondary,
                modifier = Modifier.padding(horizontal = PipeBombTheme.spacing.xs, vertical = PipeBombTheme.spacing.xs),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap)) {
                items(
                    count = history.total,
                    key = { index -> index },
                ) { index ->
                    val track = history.tracksByIndex[index]
                    if (track != null) {
                        val loadedKeys = history.tracksByIndex.toSortedMap().values.map { it.key }
                        val playIndex = loadedKeys.indexOf(track.key).coerceAtLeast(0)
                        TrackRow(
                            track = track,
                            isCurrent = track.key.toString() == currentKey,
                            isPlaying = isPlaying,
                            onClick = { onPlay(loadedKeys, playIndex) },
                            onPlay = {},
                            onMore = { onTrackMore(track) },
                        )
                    } else {
                        val chunkIndex = index / HISTORY_CHUNK_SIZE
                        LaunchedEffect(chunkIndex) { onEnsureChunk(chunkIndex) }
                        val chunkError = history.chunkErrors[chunkIndex]
                        if (chunkError != null && index % HISTORY_CHUNK_SIZE == 0) {
                            TrackChunkErrorRow(onRetry = { onEnsureChunk(chunkIndex) })
                        } else {
                            TrackSkeletonRow()
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
