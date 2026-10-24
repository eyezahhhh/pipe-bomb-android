package net.pipebomb.android

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
import net.pipebomb.android.feature.album.AlbumScreen
import net.pipebomb.android.feature.artist.ArtistScreen
import net.pipebomb.android.feature.home.HomeScreen
import net.pipebomb.android.feature.library.LibraryScreen
import net.pipebomb.android.feature.playlist.PlaylistScreen
import net.pipebomb.android.feature.settings.SettingsScreen
import net.pipebomb.android.feature.player.NowPlayingScreen
import net.pipebomb.android.feature.player.QueueScreen
import net.pipebomb.android.feature.search.SearchScreen
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
        TopLevelTab(Library, "Library", ImageVector.vectorResource(R.drawable.ic_tabler_library)),
        TopLevelTab(Settings, "Settings", ImageVector.vectorResource(R.drawable.ic_tabler_settings)),
    )

    val current = backStack.lastOrNull()
    val fullScreen = current is NowPlaying
    var selectedTab by remember { mutableStateOf<NavKey>(Home) }
    val playbackHost: PlaybackHostViewModel = hiltViewModel()
    val playbackState by playbackHost.state.collectAsStateWithLifecycle()
    val currentTrack by playbackHost.currentTrack.collectAsStateWithLifecycle()

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
            entryProvider = entryProvider {
                entry<Home> {
                    HomeScreen(
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenPlaylist = { open(PlaylistRoute(it)) },
                    )
                }
                entry<Search> {
                    SearchScreen(
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
                    )
                }
                entry<Library> {
                    LibraryScreen(
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                        onOpenArtist = { open(ArtistRoute(it)) },
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
                        onSeek = playbackHost::seekTo,
                        onShuffle = playbackHost::toggleShuffle,
                        onRepeat = playbackHost::cycleRepeat,
                        onOpenQueue = { open(Queue) },
                    )
                }
                entry<AlbumRoute> { route ->
                    AlbumScreen(
                        albumId = route.albumId,
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenArtist = { open(ArtistRoute(it)) },
                    )
                }
                entry<ArtistRoute> { route ->
                    ArtistScreen(
                        artistId = route.artistId,
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                        onOpenAlbum = { open(AlbumRoute(it)) },
                    )
                }
                entry<PlaylistRoute> { route ->
                    PlaylistScreen(
                        playlistUuid = route.playlistUuid,
                        currentKey = playbackState.currentKey?.toString(),
                        isPlaying = playbackState.isPlaying,
                        onPlay = playbackHost::play,
                    )
                }
                entry<TrackRoute> { route -> PlaceholderScreen("Track", route.trackId) }
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
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = title,
                    style = PipeBombTheme.typography.trackTitle,
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
