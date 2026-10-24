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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import net.pipebomb.android.feature.home.HomeScreen
import net.pipebomb.android.feature.library.LibraryScreen
import net.pipebomb.android.feature.playlist.PlaylistScreen
import net.pipebomb.android.feature.settings.SettingsScreen
import net.pipebomb.android.feature.player.NowPlayingScreen
import net.pipebomb.android.feature.player.QueueScreen
import net.pipebomb.android.feature.search.SearchScreen
import net.pipebomb.android.ui.components.TrackContextSheet
import net.pipebomb.android.ui.playback.MiniPlayer
import net.pipebomb.android.ui.playback.PlaybackHostViewModel

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
    val fullScreen = current is NowPlaying
    var selectedTab by remember { mutableStateOf<NavKey>(Home) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val navigationEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val playbackHost: PlaybackHostViewModel = hiltViewModel()
    val playbackState by playbackHost.state.collectAsStateWithLifecycle()
    val currentTrack by playbackHost.currentTrack.collectAsStateWithLifecycle()
    val isSaved by playbackHost.saved.collectAsStateWithLifecycle()

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

    fun back() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    Scaffold(
        containerColor = PipeBombTheme.colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!fullScreen) {
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
                        Queue -> "Queue"
                        else -> "Pipe Bomb"
                    },
                    canGoBack = backStack.size > 1,
                    onBack = { back() },
                )
            }
        },
        bottomBar = {
            if (!fullScreen) {
                Column {
                    currentTrack?.let { track ->
                        MiniPlayer(
                            track = track,
                            isPlaying = playbackState.isPlaying,
                            onOpen = { open(NowPlaying) },
                            onToggle = playbackHost::toggle,
                            onNext = playbackHost::next,
                        )
                    }
                    BottomNavigation(
                        tabs = tabs,
                        selected = selectedTab,
                        onSelect = ::selectTab,
                    )
                }
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
                    isNowPlayingRoute(toKey) || isQueueRoute(toKey) -> {
                        (slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(300, easing = navigationEasing),
                        ) + fadeIn(tween(160))) togetherWith
                            (slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(300, easing = navigationEasing),
                            ) + fadeOut(tween(160)))
                    }

                    isNowPlayingRoute(fromKey) || isQueueRoute(fromKey) -> {
                        (slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(260, easing = navigationEasing),
                        ) + fadeIn(tween(140))) togetherWith
                            (slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(260, easing = navigationEasing),
                            ) + fadeOut(tween(140)))
                    }

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
                    isNowPlayingRoute(fromKey) || isQueueRoute(fromKey) -> {
                        (slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(240, easing = navigationEasing),
                        ) + fadeIn(tween(140))) togetherWith
                            (slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(240, easing = navigationEasing),
                            ) + fadeOut(tween(140)))
                    }

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
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenPlaylist = { open(PlaylistRoute(it)) },
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<Search> {
                    SearchScreen(
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<Library> {
                    LibraryScreen(
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onOpenPlaylist = { open(PlaylistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<Settings> { SettingsScreen() }
                entry<Queue> { QueueScreen() }
                entry<NowPlaying> {
                    NowPlayingScreen(
                        track = currentTrack,
                        state = playbackState,
                        onToggle = playbackHost::toggle,
                        onNext = playbackHost::next,
                        onPrevious = playbackHost::previous,
                        onSwipePrevious = playbackHost::previousTrack,
                        onSeek = playbackHost::seekTo,
                        onShuffle = playbackHost::toggleShuffle,
                        onRepeat = playbackHost::cycleRepeat,
                        onOpenQueue = { open(Queue) },
                        onClose = { back() },
                        isSaved = isSaved,
                        onToggleSave = playbackHost::toggleSaved,
                        onOpenArtist = { open(ArtistRoute(it)) },
                    )
                }
                entry<AlbumRoute> { route ->
                    AlbumScreen(
                        albumId = route.albumId,
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenArtist = { open(ArtistRoute(it)) },
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<ArtistRoute> { route ->
                    ArtistScreen(
                        artistId = route.artistId,
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<PlaylistRoute> { route ->
                    PlaylistScreen(
                        playlistUuid = route.playlistUuid,
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onTrackMore = { menuTrack = it },
                    )
                }
                entry<TrackRoute> { route -> PlaceholderScreen("Track", route.trackId) }
            },
        )
        TrackContextSheet(
            track = menuTrack,
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

private fun isNowPlayingRoute(key: String?): Boolean = key == "NowPlaying"

private fun isQueueRoute(key: String?): Boolean = key == "Queue"

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
