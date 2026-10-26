package net.pipebomb.android.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.ReportPlaybackDto
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
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
    private val pendingKeys = ConcurrentHashMap.newKeySet<String>()

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
                if (reportedKey == keyString || pendingKeys.contains(keyString)) return@collect
                if (state.durationMs <= 0) return@collect
                val threshold = minOf(state.durationMs / 2, 240_000L)
                if (state.positionMs < threshold) return@collect
                if (attemptReport(key)) {
                    reportedKey = keyString
                } else {
                    pendingKeys.add(keyString)
                }
                if (currentKey != keyString) {
                    reportedKey = null
                }
            }
        }
        scope.launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                if (pendingKeys.isEmpty()) continue
                for (pending in pendingKeys.toList()) {
                    val key = TrackKey.parse(pending)
                    if (key == null) {
                        pendingKeys.remove(pending)
                        continue
                    }
                    if (attemptReport(key)) {
                        pendingKeys.remove(pending)
                        if (pending == currentKey) {
                            reportedKey = pending
                        }
                    }
                }
            }
        }
    }

    private suspend fun attemptReport(key: TrackKey): Boolean {
        val result = apiCall {
            apiClientFactory.requireApi().reportPlayback(
                ReportPlaybackDto(
                    pluginId = key.pluginId,
                    libraryId = key.libraryId,
                    trackId = key.trackId,
                    clientName = "Pipe Bomb Android",
                ),
            )
        }
        return result is ApiResult.Success
    }

    private companion object {
        const val RETRY_INTERVAL_MS = 60_000L
    }
}
