package net.pipebomb.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.PlaylistVisibility
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.SessionState
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val username: String = "",
    val myPlaylists: List<Playlist> = emptyList(),
    val systemPlaylists: List<Playlist> = emptyList(),
    val friendsPlaylists: List<Playlist> = emptyList(),
    val publicPlaylists: List<Playlist> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val recentArtists: List<Artist> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val userUuid = (authRepository.session.value as? SessionState.Authenticated)?.user?.uuid

    init {
        val username = (authRepository.session.value as? SessionState.Authenticated)?.user?.username.orEmpty()
        _state.value = HomeUiState(username = username)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val playlistsDeferred = async { catalogRepository.ownPlaylists() }
            val publicDeferred = async { catalogRepository.publicPlaylists() }
            val historyDeferred = async { catalogRepository.history(page = 1, pageSize = 100) }
            val playlists = playlistsDeferred.await()
            val public = publicDeferred.await()
            val history = historyDeferred.await()
            if (playlists is ApiResult.Failure || history is ApiResult.Failure) {
                val error = (playlists as? ApiResult.Failure)?.error
                    ?: (history as? ApiResult.Failure)?.error
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error?.userMessage() ?: "Unable to load home",
                )
                return@launch
            }
            val allPlaylists = (playlists as ApiResult.Success).value
            val publicPlaylists = (public as? ApiResult.Success)?.value.orEmpty()
            val recent = (history as ApiResult.Success).value.entries.mapNotNull { it.track }

            val mine = mutableListOf<Playlist>()
            val system = mutableListOf<Playlist>()
            val friends = mutableListOf<Playlist>()
            for (playlist in allPlaylists) {
                when {
                    playlist.owner.type == "system" -> system += playlist
                    playlist.owner.type == "bot" && playlist.visibility != PlaylistVisibility.Public ->
                        system += playlist
                    playlist.owner.type == "user" ->
                        if (playlist.owner.userUuid == userUuid) mine += playlist else friends += playlist
                }
            }

            _state.value = _state.value.copy(
                isLoading = false,
                myPlaylists = mine,
                systemPlaylists = system,
                friendsPlaylists = friends,
                publicPlaylists = publicPlaylists.filterNot { public ->
                    (mine + friends).any { it.uuid == public.uuid }
                },
                recentAlbums = recentAlbums(recent),
                recentArtists = recentArtists(recent),
                recentTracks = recent.distinctBy { it.key },
                error = null,
            )
        }
    }

    private fun recentAlbums(tracks: List<Track>): List<Album> {
        val seen = LinkedHashMap<String, Pair<Album, Int>>()
        tracks.forEachIndexed { index, track ->
            track.albums.orEmpty().forEach { album ->
                val key = album.uuid ?: album.identityKey ?: return@forEach
                val existing = seen[key]
                seen[key] = if (existing == null) album to index else existing.first to existing.second
            }
        }
        return seen.values.sortedBy { it.second }.map { it.first }
    }

    private fun recentArtists(tracks: List<Track>): List<Artist> {
        val seen = LinkedHashMap<String, Pair<Artist, Int>>()
        tracks.forEachIndexed { index, track ->
            track.artists.orEmpty().forEach { entry ->
                val artist = entry.artist
                val key = artist.uuid ?: artist.identityKey ?: return@forEach
                val existing = seen[key]
                seen[key] = if (existing == null) artist to index else existing.first to existing.second
            }
        }
        return seen.values.sortedBy { it.second }.map { it.first }
    }
}
