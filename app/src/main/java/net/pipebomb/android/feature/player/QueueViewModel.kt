package net.pipebomb.android.feature.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackUiState
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val controller: PlaybackController,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = controller.state

    fun playIndex(index: Int) = controller.play(controller.state.value.queue, index)
    fun clear() = controller.clearQueue()
    fun remove(index: Int) {
        val queue = controller.state.value.queue.toMutableList()
        if (index in queue.indices) {
            queue.removeAt(index)
            if (queue.isEmpty()) controller.clearQueue() else controller.play(queue, index.coerceAtMost(queue.lastIndex))
        }
    }

    fun keys(): List<TrackKey> = controller.state.value.queue
}
