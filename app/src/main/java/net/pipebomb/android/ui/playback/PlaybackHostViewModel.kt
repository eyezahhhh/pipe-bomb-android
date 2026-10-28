package net.pipebomb.android.ui.playback

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackSessionActions
import net.pipebomb.android.playback.PlaybackUiState
import javax.inject.Inject

@Immutable
data class PlaybackQueueState(
    val queue: List<TrackKey> = emptyList(),
    val currentIndex: Int = 0,
)

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val actions: PlaybackSessionActions,
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

    val queueTracks: StateFlow<Map<TrackKey, Track>> = controller.queueTracks

    val savedTracks: StateFlow<Map<TrackKey, Boolean>> = actions.savedTracks

    fun play(keys: List<TrackKey>, index: Int) = controller.play(keys, index)
    fun playNow(key: TrackKey) = controller.playNow(key)
    fun playIndex(index: Int) = controller.playIndex(index)
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun toggle() = controller.toggle()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun previousTrack() = controller.previousTrack()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun cycleRepeat() = controller.cycleRepeat()
    fun toggleShuffle() = controller.toggleShuffle()
    fun clearQueue() = controller.clearQueue()

    fun playNext(track: Track) = controller.playNext(track.key)

    fun playNextTracks(keys: List<TrackKey>) = controller.playNext(keys)

    fun addToQueue(track: Track) = controller.addToQueue(listOf(track.key))

    fun addTracksToQueue(keys: List<TrackKey>) = controller.addToQueue(keys)

    fun setSaved(track: Track, saved: Boolean) {
        viewModelScope.launch {
            actions.setSaved(track, saved)
        }
    }

    fun toggleSaved() {
        actions.toggleSaved()
    }

    fun ensureQueueTracks(indices: Iterable<Int>) {
        controller.ensureQueueTracks(indices)
    }
}