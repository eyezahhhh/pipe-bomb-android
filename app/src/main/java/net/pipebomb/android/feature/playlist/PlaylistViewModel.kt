package net.pipebomb.android.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val error: String? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()
    private var uuid: String? = null

    fun load(playlistUuid: String) {
        if (uuid == playlistUuid && _state.value.playlist != null) return
        uuid = playlistUuid
        viewModelScope.launch {
            _state.value = PlaylistUiState(isLoading = true)
            when (val result = catalogRepository.playlist(playlistUuid)) {
                is ApiResult.Failure -> _state.value = PlaylistUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> _state.value = PlaylistUiState(
                    isLoading = false,
                    playlist = result.value,
                )
            }
        }
    }
}
