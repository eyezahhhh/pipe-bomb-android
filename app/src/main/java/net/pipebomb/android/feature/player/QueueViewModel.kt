package net.pipebomb.android.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackUiState
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = controller.state

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state
                .map { it.queue }
                .distinctUntilChanged()
                .collect { keys ->
                    if (keys.isEmpty()) {
                        _tracks.value = emptyList()
                    } else {
                        val result = catalogRepository.tracks(keys)
                        _tracks.value = (result as? ApiResult.Success)?.value.orEmpty()
                    }
                }
        }
    }

    fun playIndex(index: Int) = controller.play(controller.state.value.queue, index)
    fun clear() = controller.clearQueue()
    fun remove(index: Int) {
        val queue = controller.state.value.queue.toMutableList()
        controller.removeFromQueue(index)
    }

    fun keys(): List<TrackKey> = controller.state.value.queue
}
