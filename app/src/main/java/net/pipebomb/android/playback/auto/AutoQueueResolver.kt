package net.pipebomb.android.playback.auto

import androidx.media3.common.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.playback.PlaybackResolver
import javax.inject.Inject
import javax.inject.Singleton

data class AutoResolvedQueue(
    val items: List<MediaItem>,
    val startIndex: Int,
    val tracks: List<Track>,
)

@Singleton
class AutoQueueResolver @Inject constructor(
    private val playbackResolver: PlaybackResolver,
    private val artworkStore: AutoArtworkStore,
) {
    suspend fun resolve(
        tracks: List<Track>,
        requestedIndex: Int,
        context: AutoContext,
    ): AutoResolvedQueue? {
        if (tracks.isEmpty()) return null
        val (window, offset) = queueWindow(tracks, requestedIndex)
        val selected = window.getOrNull((requestedIndex - offset).coerceIn(0, window.lastIndex))?.key
        val artwork = selected?.let { key ->
            window.firstOrNull { it.key == key }?.let { track ->
                artworkStore.load(AttributeReader.resource(track.attributes, "front"))
            }
        }
        val semaphore = Semaphore(RESOLVE_CONCURRENCY)
        val resolved = coroutineScope {
            window.mapIndexed { index, track ->
                async {
                    val media = semaphore.withPermit {
                        playbackResolver.resolve(track.key)
                    }
                    index to (media as? ApiResult.Success)?.value
                }
            }.awaitAll()
        }
        val successful = resolved.mapNotNull { (index, media) ->
            media?.let { index to it }
        }
        if (successful.isEmpty()) return null

        val tracksByOriginalIndex = window
        val items = successful.map { (index, media) ->
            val track = tracksByOriginalIndex[index]
            AutoMediaItems.trackResolved(
                track = track,
                context = context,
                resolved = media,
                artwork = if (track.key == selected) artwork else null,
            )
        }
        val successIndexes = successful.map { it.first }
        val targetOffset = successIndexes.count { it < (requestedIndex - offset) }
        return AutoResolvedQueue(
            items = items,
            startIndex = targetOffset.coerceIn(0, items.lastIndex),
            tracks = successful.map { tracksByOriginalIndex[it.first] },
        )
    }

    private fun queueWindow(tracks: List<Track>, requestedIndex: Int): Pair<List<Track>, Int> {
        if (tracks.size <= MAX_QUEUE) return tracks to 0
        val start = (requestedIndex - MAX_QUEUE / 2)
            .coerceIn(0, (tracks.size - MAX_QUEUE).coerceAtLeast(0))
        return tracks.subList(start, (start + MAX_QUEUE).coerceAtMost(tracks.size)) to start
    }

    private companion object {
        const val MAX_QUEUE = 200
        const val RESOLVE_CONCURRENCY = 4
    }
}
