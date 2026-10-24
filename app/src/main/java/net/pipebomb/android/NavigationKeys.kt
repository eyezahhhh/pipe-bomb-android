package net.pipebomb.android

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface TopLevelRoute : NavKey

@Serializable
data object Home : TopLevelRoute

@Serializable
data object Search : TopLevelRoute

@Serializable
data object Library : TopLevelRoute

@Serializable
data object Settings : TopLevelRoute

@Serializable
data class AlbumRoute(val albumId: String) : NavKey

@Serializable
data class ArtistRoute(val artistId: String) : NavKey

@Serializable
data class PlaylistRoute(val playlistUuid: String) : NavKey

@Serializable
data class TrackRoute(
    val pluginId: String,
    val libraryId: String,
    val trackId: String,
) : NavKey

@Serializable
data object NowPlaying : NavKey

@Serializable
data object Queue : NavKey
