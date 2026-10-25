package net.pipebomb.android.playback.auto

import java.util.Base64
import net.pipebomb.android.core.model.TrackKey

sealed interface AutoNode {
    data object Root : AutoNode
    data object Recent : AutoNode
    data object Playlists : AutoNode
    data object Saved : AutoNode
    data object History : AutoNode
    data class Playlist(val uuid: String) : AutoNode
    data class Album(val uuid: String) : AutoNode
    data class Artist(val uuid: String) : AutoNode
    data class SavedGroup(val type: SavedType) : AutoNode
    data class Track(val context: AutoContext, val key: TrackKey) : AutoNode

    enum class SavedType { Tracks, Albums, Artists }
}

sealed interface AutoContext {
    data class Album(val uuid: String) : AutoContext
    data class Playlist(val uuid: String) : AutoContext
    data class Artist(val uuid: String) : AutoContext
    data object SavedTracks : AutoContext
    data object History : AutoContext
    data class Search(val query: String) : AutoContext
}

object AutoIds {
    const val ROOT = "pb:root"
    const val RECENT = "pb:recent"
    const val PLAYLISTS = "pb:playlists"
    const val SAVED = "pb:saved"
    const val HISTORY = "pb:history"

    fun playlist(uuid: String) = "pb:playlist:$uuid"
    fun album(uuid: String) = "pb:album:$uuid"
    fun artist(uuid: String) = "pb:artist:$uuid"
    fun savedTracks() = "pb:saved:tracks"
    fun savedAlbums() = "pb:saved:albums"
    fun savedArtists() = "pb:saved:artists"

    fun track(context: AutoContext, key: TrackKey): String {
        val contextToken = when (context) {
            is AutoContext.Album -> "album:${context.uuid}"
            is AutoContext.Playlist -> "playlist:${context.uuid}"
            is AutoContext.Artist -> "artist:${context.uuid}"
            AutoContext.SavedTracks -> "saved:tracks"
            AutoContext.History -> "history"
            is AutoContext.Search -> "search:${encode(context.query)}"
        }
        return "pb:track:${encode(contextToken)}:${encode(key.toString())}"
    }

    fun parse(id: String): AutoNode? {
        if (id == ROOT) return AutoNode.Root
        if (id == RECENT) return AutoNode.Recent
        if (id == PLAYLISTS) return AutoNode.Playlists
        if (id == SAVED) return AutoNode.Saved
        if (id == HISTORY) return AutoNode.History
        if (id == savedTracks()) return AutoNode.SavedGroup(AutoNode.SavedType.Tracks)
        if (id == savedAlbums()) return AutoNode.SavedGroup(AutoNode.SavedType.Albums)
        if (id == savedArtists()) return AutoNode.SavedGroup(AutoNode.SavedType.Artists)
        val parts = id.split(':')
        return when {
            parts.size == 3 && parts[0] == "pb" && parts[1] == "playlist" ->
                AutoNode.Playlist(parts[2])
            parts.size == 3 && parts[0] == "pb" && parts[1] == "album" ->
                AutoNode.Album(parts[2])
            parts.size == 3 && parts[0] == "pb" && parts[1] == "artist" ->
                AutoNode.Artist(parts[2])
            parts.size == 4 && parts[0] == "pb" && parts[1] == "track" -> {
                val context = decodeContext(decode(parts[2])) ?: return null
                val key = TrackKey.parse(decode(parts[3])) ?: return null
                AutoNode.Track(context, key)
            }
            else -> null
        }
    }

    fun contextFor(node: AutoNode): AutoContext? = when (node) {
        is AutoNode.Album -> AutoContext.Album(node.uuid)
        is AutoNode.Playlist -> AutoContext.Playlist(node.uuid)
        is AutoNode.Artist -> AutoContext.Artist(node.uuid)
        AutoNode.Recent -> AutoContext.History
        AutoNode.History -> AutoContext.History
        is AutoNode.Track -> node.context
        is AutoNode.SavedGroup -> when (node.type) {
            AutoNode.SavedType.Tracks -> AutoContext.SavedTracks
            else -> null
        }
        else -> null
    }

    private fun decodeContext(token: String): AutoContext? = when {
        token == "saved:tracks" -> AutoContext.SavedTracks
        token == "history" -> AutoContext.History
        token.startsWith("album:") -> AutoContext.Album(token.removePrefix("album:"))
        token.startsWith("playlist:") -> AutoContext.Playlist(token.removePrefix("playlist:"))
        token.startsWith("artist:") -> AutoContext.Artist(token.removePrefix("artist:"))
        token.startsWith("search:") -> AutoContext.Search(decode(token.removePrefix("search:")))
        else -> null
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
