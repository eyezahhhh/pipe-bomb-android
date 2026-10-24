package net.pipebomb.android.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.ReportPlaybackDto
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackReporter @Inject constructor(
    controller: PlaybackController,
    private val apiClientFactory: ApiClientFactory,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private var reportedKey: String? = null
    private var currentKey: String? = null

    init {
        scope.launch {
            controller.state
                .map { it.currentKey?.toString() }
                .distinctUntilChanged()
                .collect {
                    currentKey = it
                    if (it != reportedKey) {
                        reportedKey = null
                    }
                }
        }
        scope.launch {
            controller.state.collect { state ->
                val key = state.currentKey ?: return@collect
                val keyString = key.toString()
                if (reportedKey == keyString) return@collect
                if (state.durationMs <= 0) return@collect
                val threshold = minOf(state.durationMs / 2, 240_000L)
                if (state.positionMs < threshold) return@collect
                reportedKey = keyString
                apiCall {
                    apiClientFactory.requireApi().reportPlayback(
                        ReportPlaybackDto(
                            pluginId = key.pluginId,
                            libraryId = key.libraryId,
                            trackId = key.trackId,
                            clientName = "Pipe Bomb Android",
                        ),
                    )
                }
                if (currentKey != keyString) {
                    reportedKey = null
                }
            }
        }
    }
}
