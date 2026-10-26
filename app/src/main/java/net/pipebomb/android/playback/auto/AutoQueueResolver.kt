package net.pipebomb.android.playback.auto

import androidx.media3.common.MediaItem
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

data class AutoResolvedQueue(
    val items: List<MediaItem>,
    val startIndex: Int,
    val tracks: List<Track>,
)

@Singleton
class AutoQueueResolver @Inject constructor(
    private val artworkStore: AutoArtworkStore,
) {
    suspend fun resolve(
        tracks: List<Track>,
        requestedIndex: Int,
        context: AutoContext,
    ): AutoResolvedQueue? {
        if (tracks.isEmpty()) return null
        val index = requestedIndex.coerceIn(tracks.indices)
        val selected = tracks[index]
        val artwork = artworkStore.load(AttributeReader.resource(selected.attributes, "front"))
        val items = tracks.mapIndexed { itemIndex, track ->
            AutoMediaItems.trackQueued(
                track = track,
                context = context,
                artwork = if (itemIndex == index) artwork else null,
            )
        }
        return AutoResolvedQueue(
            items = items,
            startIndex = index,
            tracks = tracks,
        )
    }
}
