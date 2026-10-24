package net.pipebomb.android.ui.playback

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackUiState
import javax.inject.Inject

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    private val controller: PlaybackController,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = controller.state
    val currentTrack: StateFlow<Track?> = controller.currentTrack

    fun play(keys: List<TrackKey>, index: Int) = controller.play(keys, index)
    fun playNow(key: TrackKey) = controller.playNow(key)
    fun toggle() = controller.toggle()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun cycleRepeat() = controller.cycleRepeat()
    fun toggleShuffle() = controller.toggleShuffle()
    fun clearQueue() = controller.clearQueue()
}
