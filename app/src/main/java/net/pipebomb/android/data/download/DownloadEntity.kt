package net.pipebomb.android.data.download

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.pipebomb.android.core.model.AttributeMap
import net.pipebomb.android.core.model.BufferAttribute
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.core.model.StringAttribute
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackKey: String,
    val pluginId: String,
    val libraryId: String,
    val trackId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val sessionId: String,
    val mediaUrl: String,
    val state: String,
    @ColumnInfo(defaultValue = "1") val manual: Boolean = true,
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long = 0,
    val requestedAt: Long,
    val completedAt: Long? = null,
    val lastError: String? = null,
) {
    val key: TrackKey get() = TrackKey(pluginId, libraryId, trackId)
}

enum class DownloadState {
    Pending,
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
    Unsupported,
}

data class DownloadItem(
    val entity: DownloadEntity,
) {
    val key: TrackKey get() = entity.key
    val state: DownloadState
        get() = runCatching { DownloadState.valueOf(entity.state) }
            .getOrDefault(DownloadState.Queued)
    val progress: Float
        get() = if (entity.bytesTotal > 0) {
            (entity.bytesDownloaded.toFloat() / entity.bytesTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
    val track: Track get() = entity.toTrack()
}

fun DownloadEntity.toTrack(): Track {
    val attributes = buildMap {
        put("title", StringAttribute(listOf(title), listOf(title)))
        artist?.takeIf { it.isNotBlank() }?.let {
            put("artist", StringAttribute(listOf(it), listOf(it)))
        }
        artworkUrl?.takeIf { it.isNotBlank() }?.let {
            put("front", BufferAttribute(listOf(Resource(url = it))))
        }
    }
    return Track(
        uuid = trackKey,
        trackId = trackId,
        pluginId = pluginId,
        libraryId = libraryId,
        title = title,
        attributes = AttributeMap(attributes),
    )
}
