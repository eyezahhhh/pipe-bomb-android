package net.pipebomb.android.data.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.playback.ResolvedMedia
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
) {
    val downloads: Flow<List<DownloadItem>> =
        dao.observeAll().map { entities -> entities.map(::DownloadItem) }

    val totalBytes: Flow<Long> = dao.observeTotalBytes()

    fun observe(trackKey: TrackKey): Flow<DownloadItem?> =
        dao.observe(trackKey.toString()).map { it?.let(::DownloadItem) }

    suspend fun get(trackKey: TrackKey): DownloadEntity? = dao.get(trackKey.toString())

    suspend fun completed(trackKey: TrackKey): DownloadEntity? =
        dao.get(trackKey.toString())?.takeIf { it.state == DownloadState.Completed.name }

    suspend fun all(): List<DownloadEntity> = dao.getAll()

    suspend fun enqueue(
        track: Track,
        media: ResolvedMedia,
    ): DownloadEntity {
        val now = System.currentTimeMillis()
        val existing = dao.get(track.key.toString())
        val entity = DownloadEntity(
            trackKey = track.key.toString(),
            pluginId = track.pluginId,
            libraryId = track.libraryId,
            trackId = track.trackId,
            title = DisplayText.title(track),
            artist = DisplayText.artists(track).takeIf { it.isNotBlank() },
            album = track.albums?.firstOrNull()?.let(DisplayText::title),
            artworkUrl = DisplayText.artwork(track)?.url,
            sessionId = media.sessionId,
            mediaUrl = media.url,
            state = DownloadState.Queued.name,
            requestedAt = existing?.requestedAt ?: now,
            lastError = null,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun updateProgress(
        trackKey: String,
        bytesDownloaded: Long,
        bytesTotal: Long,
        state: DownloadState,
        error: String? = null,
    ) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                state = state.name,
                bytesDownloaded = bytesDownloaded.coerceAtLeast(0),
                bytesTotal = bytesTotal.coerceAtLeast(0),
                lastError = error,
                completedAt = if (state == DownloadState.Completed) {
                    existing.completedAt ?: System.currentTimeMillis()
                } else {
                    existing.completedAt
                },
            ),
        )
    }

    suspend fun markFailed(trackKey: String, message: String) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                state = DownloadState.Failed.name,
                lastError = message,
            ),
        )
    }

    suspend fun markQueued(trackKey: String) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                state = DownloadState.Queued.name,
                lastError = null,
            ),
        )
    }

    suspend fun requeue(trackKey: TrackKey, media: ResolvedMedia) {
        val existing = dao.get(trackKey.toString()) ?: return
        dao.upsert(
            existing.copy(
                sessionId = media.sessionId,
                mediaUrl = media.url,
                state = DownloadState.Queued.name,
                lastError = null,
                completedAt = null,
                bytesDownloaded = 0,
                bytesTotal = 0,
            ),
        )
    }

    suspend fun updateSource(trackKey: String, sessionId: String, mediaUrl: String) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                sessionId = sessionId,
                mediaUrl = mediaUrl,
                lastError = null,
            ),
        )
    }

    suspend fun markPaused(trackKey: String, message: String? = null) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                state = DownloadState.Paused.name,
                lastError = message,
            ),
        )
    }

    suspend fun delete(trackKey: TrackKey) {
        dao.delete(trackKey.toString())
    }

    suspend fun delete(trackKey: String) {
        dao.delete(trackKey)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
