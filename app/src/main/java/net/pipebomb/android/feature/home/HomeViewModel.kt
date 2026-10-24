package net.pipebomb.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Playlist
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
    val playlists: List<Playlist> = emptyList(),
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

    init {
        val username = (authRepository.session.value as? SessionState.Authenticated)?.user?.username.orEmpty()
        _state.value = HomeUiState(username = username)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val playlistsDeferred = async { catalogRepository.ownPlaylists() }
            val historyDeferred = async { catalogRepository.history(page = 1, pageSize = 30) }
            val playlists = playlistsDeferred.await()
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
            val recent = (history as ApiResult.Success).value.entries.mapNotNull { it.track }
            _state.value = _state.value.copy(
                isLoading = false,
                playlists = (playlists as ApiResult.Success).value,
                recentTracks = recent.distinctBy { it.key },
                error = null,
            )
        }
    }
}
