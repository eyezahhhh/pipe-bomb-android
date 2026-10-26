package net.pipebomb.android.data.download

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.playback.ResolvedMedia
import javax.inject.Inject
import javax.inject.Singleton

data class BulkEnqueueResult(
    val queued: Int,
    val skipped: Int,
)

data class CollectionReconcileResult(
    val added: Int,
    val pendingCreated: Int,
    val releasedTrackKeys: List<String>,
)

sealed interface DownloadRemoveResult {
    data object Removed : DownloadRemoveResult
    data class Blocked(val collectionTitles: List<String>) : DownloadRemoveResult
}

@Singleton
class DownloadRepository @Inject constructor(
    private val database: DownloadDatabase,
    private val dao: DownloadDao,
) {
    val downloads: Flow<List<DownloadItem>> =
        dao.observeAll().map { entities -> entities.map(::DownloadItem) }

    val collections: Flow<List<DownloadCollectionItem>> =
        dao.observeCollections().map { rows -> rows.map { it.toItem() } }

    val totalBytes: Flow<Long> = dao.observeTotalBytes()

    fun observe(trackKey: TrackKey): Flow<DownloadItem?> =
        dao.observe(trackKey.toString()).map { it?.let(::DownloadItem) }

    suspend fun get(trackKey: TrackKey): DownloadEntity? = dao.get(trackKey.toString())

    suspend fun completed(trackKey: TrackKey): DownloadEntity? =
        dao.get(trackKey.toString())?.takeIf { it.state == DownloadState.Completed.name }

    suspend fun all(): List<DownloadEntity> = dao.getAll()

    suspend fun localMetadata(keys: Collection<TrackKey>): Map<TrackKey, Track> {
        val unique = keys.distinct()
        if (unique.isEmpty()) return emptyMap()
        return dao.getMany(unique.map { it.toString() })
            .associate { it.key to it.toTrack() }
    }

    suspend fun pending(): List<DownloadEntity> = dao.getPending()

    suspend fun hasIncompleteDownloads(): Boolean = dao.incompleteCount() > 0

    suspend fun enqueue(track: Track, media: ResolvedMedia): DownloadEntity {
        val existing = dao.get(track.key.toString())
        val entity = track.toDownloadEntity(
            state = DownloadState.Queued.name,
            sessionId = media.sessionId,
            mediaUrl = media.url,
            manual = true,
            existing = existing,
        )
        dao.upsert(entity)
        return entity
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

    suspend fun enqueueBatch(tracks: List<Track>): BulkEnqueueResult =
        database.withTransaction {
            val unique = tracks.distinctBy { it.key }
            val existing = dao.getMany(unique.map { it.key.toString() }).associateBy { it.trackKey }
            var queued = 0
            var skipped = 0
            for (track in unique) {
                val current = existing[track.key.toString()]
                when (current?.state) {
                    DownloadState.Completed.name,
                    DownloadState.Queued.name,
                    DownloadState.Downloading.name,
                    DownloadState.Pending.name,
                    DownloadState.Unsupported.name,
                    -> skipped++

                    else -> {
                        dao.upsert(
                            track.toDownloadEntity(
                                state = DownloadState.Pending.name,
                                sessionId = "",
                                mediaUrl = "",
                                manual = current?.manual ?: false,
                                existing = current,
                            ),
                        )
                        queued++
                    }
                }
            }
            BulkEnqueueResult(queued = queued, skipped = skipped)
        }

    suspend fun subscribeCollection(
        draft: DownloadCollectionDraft,
        tracks: List<Track>,
        accountUuid: String?,
        accountName: String?,
        remoteRevision: String? = null,
    ): BulkEnqueueResult = database.withTransaction {
        val now = System.currentTimeMillis()
        val collectionId = draft.type.collectionId(draft.sourceId)
        val existingCollection = dao.getCollection(collectionId)
        val collection = DownloadCollectionEntity(
            id = collectionId,
            type = draft.type.storageValue,
            sourceId = draft.sourceId,
            title = draft.title,
            artworkUrl = draft.artworkUrl,
            ownerName = draft.ownerName,
            accountUuid = accountUuid,
            accountName = accountName,
            remoteRevision = remoteRevision ?: existingCollection?.remoteRevision,
            trackCount = tracks.size,
            createdAt = existingCollection?.createdAt ?: now,
            lastSyncedAt = now,
            lastError = null,
        )
        dao.upsertCollection(collection)
        dao.deleteCollectionTracks(collectionId)

        var queued = 0
        var skipped = 0
        tracks.distinctBy { it.key }.forEachIndexed { index, track ->
            dao.upsertCollectionTracks(
                listOf(
                    DownloadCollectionTrackEntity(
                        collectionId = collectionId,
                        trackKey = track.key.toString(),
                        position = index,
                        addedAt = now,
                    ),
                ),
            )
            val current = dao.get(track.key.toString())
            when (current?.state) {
                DownloadState.Completed.name,
                DownloadState.Queued.name,
                DownloadState.Downloading.name,
                DownloadState.Pending.name,
                DownloadState.Unsupported.name,
                -> skipped++

                else -> {
                    dao.upsert(
                        track.toDownloadEntity(
                            state = DownloadState.Pending.name,
                            sessionId = "",
                            mediaUrl = "",
                            manual = current?.manual ?: false,
                            existing = current,
                        ),
                    )
                    queued++
                }
            }
        }
        BulkEnqueueResult(queued = queued, skipped = skipped)
    }

    suspend fun reconcileCollection(
        collectionId: String,
        title: String,
        artworkUrl: String?,
        ownerName: String?,
        accountUuid: String?,
        accountName: String?,
        remoteRevision: String?,
        tracks: List<Track>,
    ): CollectionReconcileResult = database.withTransaction {
        val now = System.currentTimeMillis()
        val existingCollection = dao.getCollection(collectionId)
        val existingMemberships = dao.getCollectionTracks(collectionId).associateBy { it.trackKey }
        val current = tracks.distinctBy { it.key }
        val currentKeys = current.mapTo(mutableSetOf()) { it.key.toString() }
        val existingByKey = dao.getMany(currentKeys.toList()).associateBy { it.trackKey }

        var added = 0
        var pendingCreated = 0
        current.forEachIndexed { index, track ->
            val key = track.key.toString()
            if (existingMemberships[key] == null) added++
            dao.upsertCollectionTracks(
                listOf(
                    DownloadCollectionTrackEntity(
                        collectionId = collectionId,
                        trackKey = key,
                        position = index,
                        addedAt = existingMemberships[key]?.addedAt ?: now,
                    ),
                ),
            )

            val download = existingByKey[key]
            when (download?.state) {
                DownloadState.Unsupported.name -> Unit
                DownloadState.Pending.name,
                DownloadState.Queued.name,
                DownloadState.Downloading.name,
                DownloadState.Completed.name,
                -> {
                    dao.upsert(
                        download.copy(
                            title = DisplayText.title(track),
                            artist = DisplayText.artists(track).takeIf { it.isNotBlank() },
                            album = track.albums?.firstOrNull()?.let(DisplayText::title),
                            artworkUrl = DisplayText.artwork(track)?.url,
                        ),
                    )
                }

                else -> {
                    dao.upsert(
                        track.toDownloadEntity(
                            state = DownloadState.Pending.name,
                            sessionId = "",
                            mediaUrl = "",
                            manual = download?.manual ?: false,
                            existing = download,
                        ),
                    )
                    pendingCreated++
                }
            }
        }

        val removedKeys = existingMemberships.keys - currentKeys
        for (key in removedKeys) {
            dao.deleteCollectionTrack(collectionId, key)
        }

        val released = mutableListOf<String>()
        for (key in removedKeys) {
            val download = dao.get(key) ?: continue
            if (download.manual) continue
            if (dao.countMemberships(key) > 0) continue
            dao.delete(key)
            released += key
        }

        val collection = DownloadCollectionEntity(
            id = collectionId,
            type = existingCollection?.type ?: DownloadCollectionType.Playlist.storageValue,
            sourceId = existingCollection?.sourceId.orEmpty(),
            title = title,
            artworkUrl = artworkUrl,
            ownerName = ownerName,
            accountUuid = accountUuid ?: existingCollection?.accountUuid,
            accountName = accountName ?: existingCollection?.accountName,
            remoteRevision = remoteRevision,
            trackCount = current.size,
            createdAt = existingCollection?.createdAt ?: now,
            lastSyncedAt = now,
            lastError = null,
        )
        dao.upsertCollection(collection)

        CollectionReconcileResult(
            added = added,
            pendingCreated = pendingCreated,
            releasedTrackKeys = released,
        )
    }

    suspend fun unsubscribeCollection(collectionId: String): List<String> =
        database.withTransaction {
            val removedMemberships = dao.getCollectionTracks(collectionId)
            dao.deleteCollectionTracks(collectionId)
            dao.deleteCollection(collectionId)
            val released = mutableListOf<String>()
            for (membership in removedMemberships) {
                val download = dao.get(membership.trackKey) ?: continue
                if (download.manual) continue
                if (dao.countMemberships(membership.trackKey) > 0) continue
                dao.delete(membership.trackKey)
                released += membership.trackKey
            }
            released
        }

    suspend fun remove(item: DownloadItem): DownloadRemoveResult {
        val collections = dao.getCollectionsForTrack(item.entity.trackKey)
        if (collections.isNotEmpty()) {
            return DownloadRemoveResult.Blocked(collections.map { it.title })
        }
        dao.delete(item.entity.trackKey)
        return DownloadRemoveResult.Removed
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

    suspend fun updateSource(trackKey: String, sessionId: String, mediaUrl: String) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                sessionId = sessionId,
                mediaUrl = mediaUrl,
                state = DownloadState.Queued.name,
                lastError = null,
            ),
        )
    }

    suspend fun markUnsupported(trackKey: String, message: String) {
        val existing = dao.get(trackKey) ?: return
        dao.upsert(
            existing.copy(
                state = DownloadState.Unsupported.name,
                lastError = message,
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
        database.withTransaction {
            dao.deleteAllCollectionTracks()
            dao.deleteAllCollections()
            dao.deleteAll()
        }
    }

    suspend fun getCollection(id: String): DownloadCollectionEntity? = dao.getCollection(id)

    suspend fun getCollections(): List<DownloadCollectionEntity> = dao.getCollections()

    suspend fun getCollectionTracks(id: String): List<DownloadEntity> =
        dao.getVisibleCollectionDownloads(id)

    suspend fun retryFailedMembers(collectionId: String): Int =
        database.withTransaction {
            var retried = 0
            for (membership in dao.getCollectionTracks(collectionId)) {
                val download = dao.get(membership.trackKey) ?: continue
                if (download.state != DownloadState.Failed.name) continue
                dao.upsert(download.toPendingRetry())
                retried++
            }
            retried
        }

    suspend fun retryFailedDownloads(accountUuid: String?): Int =
        database.withTransaction {
            val retryable = dao.getFailed().filter { download ->
                download.manual || dao.getCollectionsForTrack(download.trackKey).any {
                    it.accountUuid == null || it.accountUuid == accountUuid
                }
            }
            retryable.forEach { dao.upsert(it.toPendingRetry()) }
            retryable.size
        }

    private fun DownloadEntity.toPendingRetry(): DownloadEntity = copy(
        state = DownloadState.Pending.name,
        sessionId = "",
        mediaUrl = "",
        lastError = null,
        bytesDownloaded = 0,
        bytesTotal = 0,
    )

    suspend fun collectionsForTrack(trackKey: String): List<DownloadCollectionEntity> =
        dao.getCollectionsForTrack(trackKey)

    suspend fun markCollectionSynced(id: String, error: String?) {
        dao.markCollectionSynced(id, System.currentTimeMillis(), error)
    }

    suspend fun markCollectionError(id: String, error: String) {
        dao.updateCollectionError(id, error)
    }

    private fun Track.toDownloadEntity(
        state: String,
        sessionId: String,
        mediaUrl: String,
        manual: Boolean,
        existing: DownloadEntity?,
    ): DownloadEntity = DownloadEntity(
        trackKey = key.toString(),
        pluginId = pluginId,
        libraryId = libraryId,
        trackId = trackId,
        title = DisplayText.title(this),
        artist = DisplayText.artists(this).takeIf { it.isNotBlank() },
        album = albums?.firstOrNull()?.let(DisplayText::title),
        artworkUrl = DisplayText.artwork(this)?.url,
        sessionId = sessionId,
        mediaUrl = mediaUrl,
        state = state,
        manual = manual,
        bytesDownloaded = 0,
        bytesTotal = 0,
        requestedAt = existing?.requestedAt ?: System.currentTimeMillis(),
        completedAt = if (state == DownloadState.Completed.name) existing?.completedAt else null,
        lastError = null,
    )
}
