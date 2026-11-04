package net.pipebomb.android.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.ArtistEphemeralContent
import net.pipebomb.android.core.model.EphemeralContentState
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.parseIdentityRef
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

data class ArtistUiState(
    val isLoading: Boolean = true,
    val artist: Artist? = null,
    val error: String? = null,
    val ephemeral: EphemeralContentState<ArtistEphemeralContent> =
        EphemeralContentState(),
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val connectivity: ConnectivityStatus,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()
    private var artistId: String? = null
    private var ephemeralJob: Job? = null

    fun load(id: String) {
        if (artistId == id && _state.value.artist != null) {
            if (!_state.value.ephemeral.visible) retryEphemeral()
            return
        }
        artistId = id
        ephemeralJob?.cancel()
        viewModelScope.launch {
            _state.value = ArtistUiState(isLoading = true)
            when (val result = catalogRepository.artist(id)) {
                is ApiResult.Failure -> _state.value = ArtistUiState(
                    isLoading = false,
                    error = result.userMessage(),
                )

                is ApiResult.Success -> {
                    _state.value = ArtistUiState(
                        isLoading = false,
                        artist = result.value,
                    )
                    loadEphemeral(id)
                }
            }
        }
    }

    fun selectEphemeralSource(source: EphemeralSource) {
        val id = artistId ?: return
        if (_state.value.ephemeral.isIdentity) return
        if (_state.value.ephemeral.selectedSource == source) return
        ephemeralJob?.cancel()
        _state.update {
            it.copy(
                ephemeral = it.ephemeral.copy(
                    selectedSource = source,
                    isLoadingContent = true,
                    content = null,
                ),
            )
        }
        ephemeralJob = viewModelScope.launch {
            fetchEphemeralContent(id, source)
        }
    }

    fun retryEphemeral() {
        val id = artistId ?: return
        if (_state.value.artist == null || _state.value.ephemeral.visible) return
        loadEphemeral(id)
    }

    fun toggleSave() {
        val artist = _state.value.artist ?: return
        val id = artist.uuid ?: artist.identityKey ?: return
        val saved = artist.bookmarked == true
        if (artist.uuid == null && saved) return
        viewModelScope.launch {
            when (catalogRepository.setArtistSaved(id, !saved)) {
                is ApiResult.Success -> _state.update {
                    it.copy(artist = it.artist?.copy(bookmarked = !saved))
                }

                is ApiResult.Failure -> Unit
            }
        }
    }

    private fun loadEphemeral(id: String) {
        ephemeralJob?.cancel()
        if (!connectivity.isOnline.value) {
            _state.update { it.copy(ephemeral = EphemeralContentState()) }
            return
        }
        val identity = parseIdentityRef(id)
        if (identity != null) {
            _state.update {
                it.copy(
                    ephemeral = EphemeralContentState(
                        isIdentity = true,
                        isLoadingContent = true,
                    ),
                )
            }
            ephemeralJob = viewModelScope.launch {
                when (
                    val result = catalogRepository.artistEphemeralContentByIdentity(
                        identity.pluginId,
                        identity.identifierId,
                        identity.value,
                    )
                ) {
                    is ApiResult.Success -> _state.update {
                        it.copy(
                            ephemeral = EphemeralContentState(
                                isIdentity = true,
                                content = result.value,
                                selectedSource = result.value.source,
                            ),
                        )
                    }

                    is ApiResult.Failure -> _state.update {
                        it.copy(ephemeral = EphemeralContentState(isIdentity = true))
                    }
                }
            }
            return
        }

        _state.update {
            it.copy(ephemeral = EphemeralContentState(isLoadingSources = true))
        }
        ephemeralJob = viewModelScope.launch {
            when (val result = catalogRepository.artistEphemeralSources(id)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(ephemeral = EphemeralContentState())
                }

                is ApiResult.Success -> {
                    if (result.value.isEmpty()) {
                        _state.update {
                            it.copy(ephemeral = EphemeralContentState())
                        }
                    } else {
                        val previous = _state.value.ephemeral.selectedSource
                        val selected = result.value.firstOrNull {
                            it.pluginId == previous?.pluginId && it.id == previous.id
                        } ?: result.value.first()
                        _state.update {
                            it.copy(
                                ephemeral = it.ephemeral.copy(
                                    isLoadingSources = false,
                                    sources = result.value,
                                    selectedSource = selected,
                                    isLoadingContent = true,
                                    content = null,
                                ),
                            )
                        }
                        fetchEphemeralContent(id, selected)
                    }
                }
            }
        }
    }

    private suspend fun fetchEphemeralContent(id: String, source: EphemeralSource) {
        when (val result = catalogRepository.artistEphemeralContent(id, source)) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    ephemeral = it.ephemeral.copy(
                        isLoadingContent = false,
                        content = result.value,
                        selectedSource = result.value.source,
                    ),
                )
            }

            is ApiResult.Failure -> _state.update {
                it.copy(ephemeral = EphemeralContentState())
            }
        }
    }
}
