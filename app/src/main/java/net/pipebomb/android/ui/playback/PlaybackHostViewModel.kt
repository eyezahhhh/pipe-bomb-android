package net.pipebomb.android.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.playback.PlaybackController
import net.pipebomb.android.playback.PlaybackUiState
import javax.inject.Inject

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = controller.state
    val currentTrack: StateFlow<Track?> = controller.currentTrack

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            controller.currentTrack.collect { track ->
                _saved.value = track?.bookmarked == true
            }
        }
    }

    fun play(keys: List<TrackKey>, index: Int) = controller.play(keys, index)
    fun playNow(key: TrackKey) = controller.playNow(key)
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
}
