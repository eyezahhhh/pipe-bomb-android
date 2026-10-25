package net.pipebomb.android.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long = 0,
    val requestedAt: Long,
    val completedAt: Long? = null,
    val lastError: String? = null,
) {
    val key: TrackKey get() = TrackKey(pluginId, libraryId, trackId)
}

enum class DownloadState {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
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
    val track: Track
        get() = Track(
            trackId = entity.trackId,
            pluginId = entity.pluginId,
            libraryId = entity.libraryId,
            title = entity.title,
        )
}
