package net.pipebomb.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.EphemeralSearchResults
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.SearchResults
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.CatalogRepository
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: SearchResults? = null,
    val ephemeralResults: EphemeralSearchResults? = null,
    val sources: List<EphemeralSource> = emptyList(),
    val selectedSource: EphemeralSource? = null,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            when (val result = catalogRepository.ephemeralSources()) {
                is ApiResult.Success -> _state.update { it.copy(sources = result.value) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, results = null, ephemeralResults = null) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update {
                it.copy(isLoading = false, results = null, ephemeralResults = null, error = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch()
        }
    }

    fun selectSource(source: EphemeralSource?) {
        _state.update { it.copy(selectedSource = source, results = null, ephemeralResults = null) }
        if (_state.value.query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch() }
        }
    }

    private suspend fun runSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(isLoading = true, error = null) }
        val source = _state.value.selectedSource
        if (source == null) {
            when (val result = catalogRepository.search(query)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.userMessage())
                }

                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, results = result.value)
                }
            }
        } else {
            when (val result = catalogRepository.ephemeralSearch(
                pluginId = source.pluginId,
                sourceId = source.id,
                query = query,
            )) {
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.userMessage())
                }

                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, ephemeralResults = result.value)
                }
            }
        }
    }
}
