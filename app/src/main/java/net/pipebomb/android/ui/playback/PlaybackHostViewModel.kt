package net.pipebomb.android.ui.playback

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackUiState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@Immutable
data class PlaybackQueueState(
    val queue: List<TrackKey> = emptyList(),
    val currentIndex: Int = 0,
)

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = controller.state
    val currentTrack: StateFlow<Track?> = controller.currentTrack

    val currentKey: StateFlow<TrackKey?> = controller.state
        .map { it.currentKey }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value.currentKey)

    val isPlaying: StateFlow<Boolean> = controller.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value.isPlaying)

    val isBuffering: StateFlow<Boolean> = controller.state
        .map { it.isBuffering }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value.isBuffering)

    val queueState: StateFlow<PlaybackQueueState> = controller.state
        .map { PlaybackQueueState(queue = it.queue, currentIndex = it.currentIndex) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackQueueState(controller.state.value.queue, controller.state.value.currentIndex),
        )

    private val _queueTracks = MutableStateFlow<Map<TrackKey, Track>>(emptyMap())
    val queueTracks: StateFlow<Map<TrackKey, Track>> = _queueTracks.asStateFlow()
    private val tracksInFlight = ConcurrentHashMap.newKeySet<TrackKey>()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            controller.currentTrack.collect { track ->
                _saved.value = track?.bookmarked == true
                if (track != null) {
                    _queueTracks.update { it + (track.key to track) }
                }
            }
        }
        viewModelScope.launch {
            controller.state
                .map { it.queue to it.currentIndex }
                .distinctUntilChanged()
                .collect { (queue, index) ->
                    ensureQueueTracks(listOf(index - 1, index, index + 1))
                }
        }
    }

    fun play(keys: List<TrackKey>, index: Int) = controller.play(keys, index)
    fun playNow(key: TrackKey) = controller.playNow(key)
    fun playIndex(index: Int) = controller.playIndex(index)
    fun toggle() = controller.toggle()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun previousTrack() = controller.previousTrack()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun cycleRepeat() = controller.cycleRepeat()
    fun toggleShuffle() = controller.toggleShuffle()
    fun clearQueue() = controller.clearQueue()

    fun playNext(track: Track) = controller.playNext(track.key)

    fun addToQueue(track: Track) = controller.addToQueue(listOf(track.key))

    fun setSaved(track: Track, saved: Boolean) {
        viewModelScope.launch {
            if (catalogRepository.setTrackSaved(track.key, saved) is ApiResult.Success) {
                if (controller.currentTrack.value?.key == track.key) {
                    _saved.value = saved
                }
            }
        }
    }

    fun toggleSaved() {
        val track = controller.currentTrack.value ?: return
        val target = !_saved.value
        viewModelScope.launch {
            if (catalogRepository.setTrackSaved(track.key, target) is ApiResult.Success) {
                _saved.value = target
            }
        }
    }
    fun ensureQueueTracks(indices: Iterable<Int>) {
        val queue = queueState.value.queue
        val keys = indices
            .mapNotNull { queue.getOrNull(it) }
            .distinct()
            .filter { _queueTracks.value[it] == null && tracksInFlight.add(it) }
        if (keys.isEmpty()) return
        viewModelScope.launch {
            when (val result = catalogRepository.tracks(keys)) {
                is ApiResult.Success -> {
                    _queueTracks.update { current ->
                        current + result.value.associateBy { it.key }
                    }
                }

                is ApiResult.Failure -> Unit
            }
            keys.forEach(tracksInFlight::remove)
        }
    }
}