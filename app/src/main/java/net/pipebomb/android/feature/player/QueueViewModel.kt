package net.pipebomb.android.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.data.TrackMetadataRepository
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.RepeatMode
import javax.inject.Inject

data class QueueUiState(
    val queue: List<TrackKey> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
)

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val metadata: TrackMetadataRepository,
) : ViewModel() {
    val state: StateFlow<QueueUiState> = controller.state
        .map {
            QueueUiState(
                queue = it.queue,
                currentIndex = it.currentIndex,
                isPlaying = it.isPlaying,
                shuffle = it.shuffle,
                repeat = it.repeat,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            controller.state.value.let {
                QueueUiState(
                    queue = it.queue,
                    currentIndex = it.currentIndex,
                    isPlaying = it.isPlaying,
                    shuffle = it.shuffle,
                    repeat = it.repeat,
                )
            },
        )

    private val _tracks = MutableStateFlow<Map<TrackKey, Track>>(emptyMap())
    val tracks: StateFlow<Map<TrackKey, Track>> = _tracks.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state
                .map { it.queue }
                .distinctUntilChanged()
                .collect { keys ->
                    if (keys.isEmpty()) {
                        _tracks.value = emptyMap()
                    } else {
                        _tracks.value = metadata.local(keys)
                        _tracks.update { current -> current + metadata.refresh(keys) }
                    }
                }
        }
    }

    fun playIndex(index: Int) = controller.playIndex(index)
    fun clear() = controller.clearQueue()
    fun remove(index: Int) = controller.removeFromQueue(index)
}
