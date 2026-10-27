package net.pipebomb.android.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.di.ApplicationScope

@Singleton
class PlaybackSessionActions @Inject constructor(
    private val controller: PlaybackController,
    private val catalogRepository: CatalogRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        scope.launch {
            controller.currentTrack.collect { track ->
                _saved.value = track?.bookmarked == true
            }
        }
    }

    suspend fun setSaved(track: Track, saved: Boolean): Boolean {
        if (catalogRepository.setTrackSaved(track.key, saved) !is ApiResult.Success) {
            return false
        }
        if (controller.currentTrack.value?.key == track.key) {
            controller.updateCurrentTrackSaved(saved)
            _saved.value = saved
        }
        return true
    }

    fun toggleSaved() {
        val track = controller.currentTrack.value ?: return
        scope.launch {
            setSaved(track, !_saved.value)
        }
    }

    fun toggleShuffle() = controller.toggleShuffle()

    fun cycleRepeat() = controller.cycleRepeat()

    companion object {
        const val COMMAND_SAVE = "net.pipebomb.android.SAVE"
        const val COMMAND_SHUFFLE = "net.pipebomb.android.SHUFFLE"
        const val COMMAND_REPEAT = "net.pipebomb.android.REPEAT"
    }
}
