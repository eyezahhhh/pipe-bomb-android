package net.pipebomb.android.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

data class ArtistUiState(
    val isLoading: Boolean = true,
    val artist: Artist? = null,
    val error: String? = null,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()
    private var artistId: String? = null

    fun load(id: String) {
        if (artistId == id && _state.value.artist != null) return
        artistId = id
        viewModelScope.launch {
            _state.value = ArtistUiState(isLoading = true)
            when (val result = catalogRepository.artist(id)) {
                is ApiResult.Failure -> _state.value = ArtistUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> _state.value = ArtistUiState(
                    isLoading = false,
                    artist = result.value,
                )
            }
        }
    }

    fun toggleSave() {
        val artist = _state.value.artist ?: return
        val uuid = artist.uuid ?: return
        val saved = artist.bookmarked == true
        viewModelScope.launch {
            when (catalogRepository.setArtistSaved(uuid, !saved)) {
                is ApiResult.Success -> _state.update {
                    it.copy(artist = it.artist?.copy(bookmarked = !saved))
                }

                is ApiResult.Failure -> Unit
            }
        }
    }
}
