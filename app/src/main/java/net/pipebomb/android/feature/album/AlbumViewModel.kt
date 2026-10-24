package net.pipebomb.android.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

data class AlbumUiState(
    val isLoading: Boolean = true,
    val album: Album? = null,
    val error: String? = null,
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    private var albumId: String? = null

    fun load(id: String) {
        if (albumId == id && _state.value.album != null) return
        albumId = id
        viewModelScope.launch {
            _state.value = AlbumUiState(isLoading = true)
            when (val result = catalogRepository.album(id)) {
                is ApiResult.Failure -> _state.value = AlbumUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> _state.value = AlbumUiState(
                    isLoading = false,
                    album = result.value,
                )
            }
        }
    }

    fun toggleSave() {
        val album = _state.value.album ?: return
        val uuid = album.uuid ?: return
        val saved = album.bookmarked == true
        viewModelScope.launch {
            when (catalogRepository.setAlbumSaved(uuid, !saved)) {
                is ApiResult.Success -> _state.update {
                    it.copy(album = it.album?.copy(bookmarked = !saved))
                }

                is ApiResult.Failure -> Unit
            }
        }
    }
}
