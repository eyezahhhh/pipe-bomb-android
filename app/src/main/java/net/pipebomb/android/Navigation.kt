package net.pipebomb.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.feature.album.AlbumScreen
import net.pipebomb.android.feature.artist.ArtistScreen
import net.pipebomb.android.feature.downloads.DownloadEffect
import net.pipebomb.android.feature.downloads.DownloadHostViewModel
import net.pipebomb.android.data.download.DownloadCollectionItem
import net.pipebomb.android.data.download.DownloadCollectionType
import net.pipebomb.android.data.download.DownloadState
import net.pipebomb.android.feature.home.HomeScreen
import net.pipebomb.android.feature.library.LibraryScreen
import net.pipebomb.android.feature.playlist.PlaylistScreen
import net.pipebomb.android.feature.settings.SettingsScreen
import net.pipebomb.android.feature.search.SearchScreen
import net.pipebomb.android.ui.components.LocalDownloadedTrackKeys
import net.pipebomb.android.ui.components.OfflineBanner
import net.pipebomb.android.ui.components.PipeBombSnackbarHost
import net.pipebomb.android.ui.components.TrackContextSheet
import net.pipebomb.android.ui.playback.PlayerHost
import net.pipebomb.android.ui.playback.PlaybackHostViewModel
import net.pipebomb.android.ui.ConnectivityViewModel

private data class TopLevelTab(
    val route: TopLevelRoute,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Home)
    val tabs = listOf(
        TopLevelTab(Home, "Home", ImageVector.vectorResource(R.drawable.ic_tabler_home)),
        TopLevelTab(Search, "Search", ImageVector.vectorResource(R.drawable.ic_tabler_search)),
        TopLevelTab(Library, "Library", ImageVector.vectorResource(R.drawable.ic_tabler_bookmark)),
        TopLevelTab(Settings, "Settings", ImageVector.vectorResource(R.drawable.ic_tabler_user_cog)),
    )

    val current = backStack.lastOrNull()
    var selectedTab by remember { mutableStateOf<NavKey>(Home) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var contentRefreshKey by remember { mutableIntStateOf(0) }
    val navigationEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val playbackHost: PlaybackHostViewModel = hiltViewModel()
    val downloadHost: DownloadHostViewModel = hiltViewModel()
    val downloadState by downloadHost.state.collectAsStateWithLifecycle()
    val downloadedTrackKeys = remember(downloadState.downloads) {
        downloadState.downloads
            .filter { it.state == DownloadState.Completed }
            .mapTo(mutableSetOf()) { it.entity.trackKey }
    }
    val connectivity: ConnectivityViewModel = hiltViewModel()
    val isOnline by connectivity.isOnline.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var downloadsRequestKey by remember { mutableIntStateOf(0) }
    var blockedRemovalTitles by remember { mutableStateOf<List<String>?>(null) }
    var previousOnline by remember { mutableStateOf(isOnline) }
    val currentKey by playbackHost.currentKey.collectAsStateWithLifecycle()
    val savedTracks by playbackHost.savedTracks.collectAsStateWithLifecycle()
    val playing by playbackHost.isPlaying.collectAsStateWithLifecycle()
    val playbackQueue by playbackHost.queueState.collectAsStateWithLifecycle()
    var miniRect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(playbackQueue.queue.isEmpty()) {
        if (playbackQueue.queue.isEmpty()) playerOpen = false
    }

    LaunchedEffect(Unit) {
        downloadHost.effects.collect { effect ->
            when (effect) {
                is DownloadEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is DownloadEffect.BlockedRemoval -> blockedRemovalTitles = effect.collectionTitles
            }
        }
    }
    LaunchedEffect(isOnline) {
        if (!previousOnline && isOnline) {
            snackbarHostState.showSnackbar("Back online · updating downloads")
            downloadHost.syncCollections()
            downloadHost.refresh()
        }
        previousOnline = isOnline
    }

    fun selectTab(route: TopLevelRoute) {
        if (selectedTab == route && backStack.size == 1) return
        while (backStack.isNotEmpty()) {
            backStack.removeLastOrNull()
        }
        backStack.add(route)
        selectedTab = route
    }

    fun open(route: NavKey) {
        backStack.add(route)
    }

    fun openDownloads() {
        downloadsRequestKey++
        selectTab(Library)
    }

    fun back() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    CompositionLocalProvider(LocalDownloadedTrackKeys provides downloadedTrackKeys) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PipeBombTheme.colors.bgBase,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { PipeBombSnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopBar(
                        title = when (current) {
                            Home -> "Home"
                            Search -> "Search"
                            Library -> "Library"
                            Settings -> "Settings"
                            is AlbumRoute -> "Album"
                            is ArtistRoute -> "Artist"
                            is PlaylistRoute -> "Playlist"
                            is TrackRoute -> "Track"
                            else -> "Pipe Bomb"
                        },
                        canGoBack = backStack.size > 1,
                        onBack = { back() },
                    )
                    if (!isOnline) OfflineBanner(onOpenDownloads = ::openDownloads)
                }
            },
            bottomBar = {
                Column {
                    if (playbackQueue.queue.isNotEmpty()) {
                        MiniPlayerSlot(
                            onRectChanged = { rect ->
                                if (rect != miniRect) miniRect = rect
                            },
                        )
                    }
                    BottomNavigation(
                        tabs = tabs,
                        selected = selectedTab,
                        onSelect = ::selectTab,
                    )
                }
            },
        ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding),
            onBack = { back() },
            transitionSpec = {
                val fromKey = initialState.key as? String
                val toKey = targetState.key as? String
                when {
                    isTopLevelRoute(fromKey) && isTopLevelRoute(toKey) -> {
                        val direction = if (tabIndex(toKey) >= tabIndex(fromKey)) 1 else -1
                        (fadeIn(tween(160)) + slideInHorizontally(
                            animationSpec = tween(220, easing = navigationEasing),
                        ) { fullWidth -> direction * fullWidth }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(
                                animationSpec = tween(220, easing = navigationEasing),
                            ) { fullWidth -> -direction * fullWidth })
                    }

                    routeDepth(toKey) > routeDepth(fromKey) -> {
                        (slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(280, easing = navigationEasing),
                        ) + fadeIn(tween(160))) togetherWith
                            (slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(280, easing = navigationEasing),
                            ) + fadeOut(tween(160)))
                    }

                    routeDepth(toKey) < routeDepth(fromKey) -> {
                        (slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(280, easing = navigationEasing),
                        ) + fadeIn(tween(160))) togetherWith
                            (slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(280, easing = navigationEasing),
                            ) + fadeOut(tween(160)))
                    }

                    else -> {
                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.99f))
                    }
                }
            },
            popTransitionSpec = {
                val fromKey = initialState.key as? String
                val toKey = targetState.key as? String
                when {
                    routeDepth(toKey) < routeDepth(fromKey) -> {
                        (slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(240, easing = navigationEasing),
                        ) + fadeIn(tween(140))) togetherWith
                            (slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(240, easing = navigationEasing),
                            ) + fadeOut(tween(140)))
                    }

                    else -> {
                        (fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.99f)) togetherWith
                            (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.99f))
                    }
                }
            },
            entryProvider = entryProvider {
                entry<Home> {
                    HomeScreen(
                        currentKey = currentKey?.toString(),
                        isPlaying = playing,
                        onPlay = playbackHost::play,
                        onOpenPlaylist = { open(PlaylistRoute(it)) },
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                        isOnline = isOnline,
                        downloads = downloadState.downloads,
                        collections = downloadState.collections,
                        onOpenDownloads = {
                            downloadsRequestKey++
                            selectTab(Library)
                        },
                        onOpenCollection = { collection ->
                            when (collection.type) {
                                DownloadCollectionType.Playlist -> open(PlaylistRoute(collection.sourceId))
                                DownloadCollectionType.Album -> open(AlbumRoute(collection.sourceId))
                            }
                        },
                        refreshKey = contentRefreshKey,
                    )
                }
                entry<Search> {
                    SearchScreen(
                        currentKey = currentKey?.toString(),
                        isPlaying = playing,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                        isOnline = isOnline,
                        downloads = downloadState.downloads,
                        collections = downloadState.collections,
                        onOpenCollection = { collection ->
                            when (collection.type) {
                                DownloadCollectionType.Playlist -> open(PlaylistRoute(collection.sourceId))
                                DownloadCollectionType.Album -> open(AlbumRoute(collection.sourceId))
                            }
                        },
                    )
                }
                entry<Library> {
                    LibraryScreen(
                        currentKey = currentKey?.toString(),
                        isPlaying = playing,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onOpenPlaylist = { open(PlaylistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                        downloads = downloadState.downloads,
                        collections = downloadState.collections,
                        storageAvailable = downloadState.storageAvailable,
                        reservePaused = downloadState.reservePaused,
                        mobileDataPaused = downloadState.mobileDataPaused,
                        isOnline = isOnline,
                        downloadsRequestKey = downloadsRequestKey,
                        accountUuid = downloadState.accountUuid,
                        accountName = downloadState.accountName,
                        onRetryDownload = downloadHost::retry,
                        onRemoveDownload = downloadHost::remove,
                        onResumeDownloads = downloadHost::resume,
                        onPlayDownload = { playbackHost.playNow(it.key) },
                        onOpenCollection = { collection ->
                            when (collection.type) {
                                DownloadCollectionType.Playlist -> open(PlaylistRoute(collection.sourceId))
                                DownloadCollectionType.Album -> open(AlbumRoute(collection.sourceId))
                            }
                        },
                        onStopKeepingCollection = downloadHost::stopKeeping,
                        refreshKey = contentRefreshKey,
                    )
                }
                entry<Settings> { SettingsScreen() }
                entry<AlbumRoute> { route ->
                    AlbumScreen(
                        albumId = route.albumId,
                        currentKey = currentKey?.toString(),
                        isPlaying = playing,
                        onPlay = playbackHost::play,
                        onPlayNext = playbackHost::playNextTracks,
                        onAddToQueue = playbackHost::addTracksToQueue,
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                        downloadCollection = downloadState.byCollection[
                            DownloadCollectionType.Album.collectionId(route.albumId)
                        ],
                        isOnline = isOnline,
                        downloadBusy = downloadState.bulkInProgress,
                        onKeepDownloaded = downloadHost::subscribeCollection,
                        onStopKeeping = downloadHost::stopKeeping,
                        onSyncCollection = downloadHost::syncCollection,
                    )
                }
                entry<ArtistRoute> { route ->
                    ArtistScreen(
                        artistId = route.artistId,
                        currentKey = currentKey?.toString(),
                        isPlaying = playing,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<PlaylistRoute> { route ->
                    PlaylistScreen(
                        playlistUuid = route.playlistUuid,
                        currentKey = currentKey?.toString(),
                        isPlaying = playing,
                        onPlay = playbackHost::play,
                        onTrackMore = { menuTrack = it },
                        onPlayNext = playbackHost::playNextTracks,
                        onAddToQueue = playbackHost::addTracksToQueue,
                        onDeleted = {
                            downloadState.byCollection[
                                DownloadCollectionType.Playlist.collectionId(route.playlistUuid)
                            ]?.let(downloadHost::stopKeeping)
                            contentRefreshKey++
                            back()
                        },
                        downloadCollection = downloadState.byCollection[
                            DownloadCollectionType.Playlist.collectionId(route.playlistUuid)
                        ],
                        isOnline = isOnline,
                        downloadBusy = downloadState.bulkInProgress,
                        onKeepDownloaded = downloadHost::subscribeCollection,
                        onStopKeeping = downloadHost::stopKeeping,
                        onSyncCollection = downloadHost::syncCollection,
                    )
                }
                entry<TrackRoute> { route -> PlaceholderScreen("Track", route.trackId) }
            },
        )
        TrackContextSheet(
            track = menuTrack?.let { track ->
                savedTracks[track.key]?.let { track.copy(bookmarked = it) } ?: track
            },
            onDismiss = { menuTrack = null },
            onPlay = { track -> playbackHost.play(listOf(track.key), 0) },
            onPlayNext = playbackHost::playNext,
            onAddToQueue = playbackHost::addToQueue,
            onToggleSaved = playbackHost::setSaved,
            onOpenArtist = {
                menuTrack = null
                open(ArtistRoute(it))
            },
            onOpenAlbum = {
                menuTrack = null
                open(AlbumRoute(it))
            },
            downloadItem = menuTrack?.let { downloadState.byTrack[it.key.toString()] },
            downloadsAvailable = downloadState.storageAvailable && isOnline,
            onDownload = downloadHost::start,
            onRemoveDownload = downloadHost::remove,
        )
        blockedRemovalTitles?.let { titles ->
            AlertDialog(
                onDismissRequest = { blockedRemovalTitles = null },
                containerColor = PipeBombTheme.colors.bgElevated,
                title = {
                    Text(
                        text = "Managed by a downloaded collection",
                        style = PipeBombTheme.typography.sectionHeading,
                        color = PipeBombTheme.colors.fgPrimary,
                    )
                },
                text = {
                    Text(
                        text = "This track is included in ${titles.joinToString(", ") { "\"$it\"" }}. " +
                            "Stop keeping that collection to remove its downloads.",
                        style = PipeBombTheme.typography.bodySecondary,
                        color = PipeBombTheme.colors.fgSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { blockedRemovalTitles = null }) {
                        Text("OK", color = PipeBombTheme.colors.accentPrimary)
                    }
                },
            )
        }
        }
        if (playbackQueue.queue.isNotEmpty()) {
            PlayerHost(
                playbackHost = playbackHost,
                miniRect = miniRect,
                open = playerOpen,
                onOpenChange = { playerOpen = it },
                onOpenArtist = {
                    playerOpen = false
                    open(ArtistRoute(it))
                },
                onClearQueue = playbackHost::clearQueue,
                onRemoveFromQueue = playbackHost::removeFromQueue,
                downloadState = downloadState,
                isOnline = isOnline,
                onDownload = downloadHost::start,
                onRemoveDownload = downloadHost::remove,
            )
        }
    }
    }
}

@Composable
private fun MiniPlayerSlot(onRectChanged: (Rect) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(
                horizontal = PipeBombTheme.spacing.sm,
                vertical = PipeBombTheme.spacing.xs,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { onRectChanged(it.boundsInWindow()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, canGoBack: Boolean, onBack: () -> Unit) {
    val colors = PipeBombTheme.colors
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
            ) {
                if (!canGoBack) {
                    Image(
                        painter = painterResource(R.drawable.pipebomb_logo),
                        contentDescription = "Pipe Bomb",
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    text = title,
                    style = PipeBombTheme.typography.input,
                    color = colors.fgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (canGoBack) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_chevron_left),
                        contentDescription = "Back",
                        tint = colors.fgPrimary,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bgBase,
            titleContentColor = colors.fgPrimary,
        ),
    )
}

@Composable
private fun BottomNavigation(
    tabs: List<TopLevelTab>,
    selected: NavKey,
    onSelect: (TopLevelRoute) -> Unit,
) {
    val colors = PipeBombTheme.colors
    NavigationBar(
        containerColor = colors.bgSurface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab.route,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = PipeBombTheme.typography.metadata,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accentPrimary,
                    selectedTextColor = colors.accentPrimary,
                    unselectedIconColor = colors.fgSecondary,
                    unselectedTextColor = colors.fgTertiary,
                    indicatorColor = colors.accentSubtle,
                ),
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    val colors = PipeBombTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
            modifier = Modifier.width(280.dp),
        ) {
            Text(
                text = title,
                style = PipeBombTheme.typography.sectionHeading,
                color = colors.fgPrimary,
            )
            Text(
                text = subtitle,
                style = PipeBombTheme.typography.bodySecondary,
                color = colors.fgTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private val topLevelRouteKeys = setOf("Home", "Search", "Library", "Settings")

private fun isTopLevelRoute(key: String?): Boolean = key in topLevelRouteKeys

private fun tabIndex(key: String?): Int = topLevelRouteKeys.indexOf(key)

private fun routeDepth(key: String?): Int = when {
    key == null -> 0
    key in topLevelRouteKeys -> 0
    key.startsWith("AlbumRoute") ||
        key.startsWith("ArtistRoute") ||
        key.startsWith("PlaylistRoute") ||
        key.startsWith("TrackRoute") -> 1
    else -> 2
}
