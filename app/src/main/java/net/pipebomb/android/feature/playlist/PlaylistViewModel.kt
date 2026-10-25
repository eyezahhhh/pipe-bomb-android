package net.pipebomb.android.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.PlaylistMember
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.User
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.data.download.offlinePlaylist
import net.pipebomb.android.data.SessionState
import javax.inject.Inject

const val PLAYLIST_CHUNK_SIZE = 50

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val totalCount: Int = 0,
    val tracksByIndex: Map<Int, Track> = emptyMap(),
    val loadingChunks: Set<Int> = emptySet(),
    val chunkErrors: Map<Int, String> = emptyMap(),
    val error: String? = null,
    val isOwner: Boolean = false,
    val members: List<PlaylistMember> = emptyList(),
    val membersLoading: Boolean = false,
    val membersLoaded: Boolean = false,
    val membersError: String? = null,
    val userSearchResults: List<User> = emptyList(),
    val userSearchLoading: Boolean = false,
    val memberActionUserUuid: String? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val deleted: Boolean = false,
    val isOfflineCopy: Boolean = false,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val connectivity: ConnectivityStatus,
    authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private var uuid: String? = null
    private val chunkLock = Any()
    private val chunksInFlight = mutableSetOf<Int>()
    private val currentUserUuid =
        (authRepository.session.value as? SessionState.Authenticated)?.user?.uuid

    fun load(playlistUuid: String) {
        if (uuid == playlistUuid && _state.value.playlist != null) return
        uuid = playlistUuid
        chunksInFlight.clear()
        viewModelScope.launch {
            _state.value = PlaylistUiState(isLoading = true)
            if (!connectivity.isOnline.value) {
                loadOffline(playlistUuid)
                return@launch
            }
            when (val result = catalogRepository.playlist(playlistUuid)) {
                is ApiResult.Failure -> loadOffline(playlistUuid, result.userMessage())
                is ApiResult.Success -> {
                    val playlist = result.value
                    val initial = playlist.tracks.orEmpty()
                    val total = playlist.trackCount ?: initial.size
                    val tracksByIndex = initial.mapIndexed { index, entry -> index to entry.track }.toMap()
                    _state.value = PlaylistUiState(
                        isLoading = false,
                        playlist = playlist,
                        totalCount = total,
                        tracksByIndex = tracksByIndex,
                        isOwner = playlist.owner.type == "user" &&
                            playlist.owner.userUuid == currentUserUuid,
                    )
                }
            }
        }
    }

    private suspend fun loadOffline(playlistUuid: String, fallbackError: String? = null) {
        val playlist = downloadRepository.offlinePlaylist(playlistUuid)
        _state.value = if (playlist != null) {
            val tracks = playlist.tracks.orEmpty()
            PlaylistUiState(
                isLoading = false,
                playlist = playlist,
                totalCount = tracks.size,
                tracksByIndex = tracks.mapIndexed { index, entry -> index to entry.track }.toMap(),
                isOfflineCopy = true,
            )
        } else {
            PlaylistUiState(
                isLoading = false,
                error = fallbackError ?: "This playlist isn't downloaded. Connect to view it.",
            )
        }
    }

    fun ensureChunk(chunkIndex: Int) {
        val playlist = _state.value.playlist ?: return
        val total = _state.value.totalCount
        val start = chunkIndex * PLAYLIST_CHUNK_SIZE
        if (start >= total) return
        val end = minOf(total, start + PLAYLIST_CHUNK_SIZE)
        val alreadyLoaded = (start until end).all { _state.value.tracksByIndex.containsKey(it) }
        if (alreadyLoaded) return

        synchronized(chunkLock) {
            if (!chunksInFlight.add(chunkIndex)) return
            _state.update {
                it.copy(
                    loadingChunks = it.loadingChunks + chunkIndex,
                    chunkErrors = it.chunkErrors - chunkIndex,
                )
            }
        }

        viewModelScope.launch {
            val amount = end - start
            val result = catalogRepository.playlistTrackPage(
                uuid = playlist.uuid,
                offset = start,
                amount = amount,
            )
            when (result) {
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            loadingChunks = it.loadingChunks - chunkIndex,
                            chunkErrors = it.chunkErrors + (chunkIndex to result.error.userMessage()),
                        )
                    }
                }

                is ApiResult.Success -> {
                    _state.update { current ->
                        val merged = current.tracksByIndex.toMutableMap()
                        result.value.forEachIndexed { localIndex, entry ->
                            merged[start + localIndex] = entry.track
                        }
                        current.copy(
                            tracksByIndex = merged,
                            loadingChunks = current.loadingChunks - chunkIndex,
                            chunkErrors = current.chunkErrors - chunkIndex,
                        )
                    }
                }
            }
            synchronized(chunkLock) { chunksInFlight.remove(chunkIndex) }
        }
    }

    suspend fun loadAllTracks(): List<Track> {
        val playlist = _state.value.playlist ?: return emptyList()
        val total = _state.value.totalCount
        val loaded = _state.value.tracksByIndex
        if (loaded.size >= total) {
            return (0 until total).mapNotNull(loaded::get)
        }
        return when (val all = catalogRepository.allPlaylistTracks(playlist.uuid)) {
            is ApiResult.Success -> all.value.map { it.track }
            is ApiResult.Failure -> (0 until total).mapNotNull(loaded::get)
        }
    }
    fun loadMembers(force: Boolean = false) {
        val playlist = _state.value.playlist ?: return
        if (!force && (_state.value.membersLoaded || _state.value.membersLoading)) return
        viewModelScope.launch {
            _state.update { it.copy(membersLoading = true, membersError = null) }
            when (val result = catalogRepository.playlistMembers(playlist.uuid)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(membersLoading = false, membersError = result.error.userMessage())
                }

                is ApiResult.Success -> _state.update {
                    it.copy(
                        members = result.value,
                        membersLoading = false,
                        membersLoaded = true,
                        membersError = null,
                    )
                }
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(userSearchResults = emptyList(), userSearchLoading = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(userSearchLoading = true, membersError = null) }
            when (val result = catalogRepository.searchUsers(query)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(userSearchLoading = false, membersError = result.error.userMessage())
                }

                is ApiResult.Success -> _state.update {
                    it.copy(userSearchResults = result.value, userSearchLoading = false)
                }
            }
        }
    }

    fun addMember(user: User) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    memberActionUserUuid = user.uuid,
                    membersError = null,
                    userSearchResults = emptyList(),
                )
            }
            when (
                val result = catalogRepository.setPlaylistMember(
                    uuid = playlist.uuid,
                    userUuid = user.uuid,
                    role = ROLE_VIEWER,
                )
            ) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        memberActionUserUuid = null,
                        membersError = result.error.userMessage(),
                    )
                }

                is ApiResult.Success -> {
                    _state.update { it.copy(memberActionUserUuid = null, membersLoaded = false) }
                    loadMembers(force = true)
                }
            }
        }
    }

    fun updateMemberRole(member: PlaylistMember, canEdit: Boolean) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(memberActionUserUuid = member.userUuid, membersError = null)
            }
            when (
                val result = catalogRepository.setPlaylistMember(
                    uuid = playlist.uuid,
                    userUuid = member.userUuid,
                    role = if (canEdit) ROLE_COLLABORATOR else ROLE_VIEWER,
                )
            ) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        memberActionUserUuid = null,
                        membersError = result.error.userMessage(),
                    )
                }

                is ApiResult.Success -> {
                    _state.update { it.copy(memberActionUserUuid = null, membersLoaded = false) }
                    loadMembers(force = true)
                }
            }
        }
    }

    fun removeMember(member: PlaylistMember) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(memberActionUserUuid = member.userUuid, membersError = null)
            }
            when (val result = catalogRepository.removePlaylistMember(playlist.uuid, member.userUuid)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        memberActionUserUuid = null,
                        membersError = result.error.userMessage(),
                    )
                }

                is ApiResult.Success -> {
                    _state.update { it.copy(memberActionUserUuid = null, membersLoaded = false) }
                    loadMembers(force = true)
                }
            }
        }
    }

    fun deletePlaylist() {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, deleteError = null) }
            when (val result = catalogRepository.deletePlaylist(playlist.uuid)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(isDeleting = false, deleteError = result.error.userMessage())
                }

                is ApiResult.Success -> _state.update { it.copy(isDeleting = false, deleted = true) }
            }
        }
    }

    private companion object {
        const val ROLE_COLLABORATOR = "collaborator"
        const val ROLE_VIEWER = "viewer"
    }
}