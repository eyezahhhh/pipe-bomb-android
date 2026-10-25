package net.pipebomb.android.data.download

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class DownloadCollectionWithCounts(
    val id: String,
    val type: String,
    val sourceId: String,
    val title: String,
    val artworkUrl: String?,
    val ownerName: String?,
    val accountUuid: String?,
    val accountName: String?,
    val remoteRevision: String?,
    val trackCount: Int,
    val createdAt: Long,
    val lastSyncedAt: Long?,
    val lastError: String?,
    val memberCount: Int,
    val downloadedCount: Int,
) {
    fun toItem(): DownloadCollectionItem = DownloadCollectionItem(
        entity = DownloadCollectionEntity(
            id = id,
            type = type,
            sourceId = sourceId,
            title = title,
            artworkUrl = artworkUrl,
            ownerName = ownerName,
            accountUuid = accountUuid,
            accountName = accountName,
            remoteRevision = remoteRevision,
            trackCount = trackCount,
            createdAt = createdAt,
            lastSyncedAt = lastSyncedAt,
            lastError = lastError,
        ),
        memberCount = memberCount,
        downloadedCount = downloadedCount,
    )
}

@Dao
interface DownloadDao {
    @Query(
        "SELECT * FROM downloads " +
            "WHERE state NOT IN ('Pending', 'Unsupported') " +
            "ORDER BY requestedAt DESC",
    )
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackKey = :trackKey")
    fun observe(trackKey: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE trackKey = :trackKey")
    suspend fun get(trackKey: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE trackKey IN (:trackKeys)")
    suspend fun getMany(trackKeys: List<String>): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY requestedAt ASC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'Pending' ORDER BY requestedAt ASC")
    suspend fun getPending(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DownloadEntity>)

    @Query("DELETE FROM downloads WHERE trackKey = :trackKey")
    suspend fun delete(trackKey: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("DELETE FROM download_collections")
    suspend fun deleteAllCollections()

    @Query("DELETE FROM download_collection_tracks")
    suspend fun deleteAllCollectionTracks()

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads")
    suspend fun totalBytes(): Long

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM download_collection_tracks t
             WHERE t.collectionId = c.id) AS memberCount,
            (SELECT COUNT(*) FROM downloads d
             INNER JOIN download_collection_tracks t ON d.trackKey = t.trackKey
             WHERE t.collectionId = c.id
               AND d.state = 'Completed') AS downloadedCount
        FROM download_collections c
        ORDER BY c.createdAt ASC
        """,
    )
    fun observeCollections(): Flow<List<DownloadCollectionWithCounts>>

    @Query("SELECT * FROM download_collections WHERE id = :id")
    suspend fun getCollection(id: String): DownloadCollectionEntity?

    @Query("SELECT * FROM download_collections ORDER BY createdAt ASC")
    suspend fun getCollections(): List<DownloadCollectionEntity>

    @Upsert
    suspend fun upsertCollection(collection: DownloadCollectionEntity)

    @Query("DELETE FROM download_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("UPDATE download_collections SET lastError = :error WHERE id = :id")
    suspend fun updateCollectionError(id: String, error: String?)

    @Query("UPDATE download_collections SET lastError = :error, lastSyncedAt = :syncedAt WHERE id = :id")
    suspend fun markCollectionSynced(id: String, syncedAt: Long, error: String?)

    @Query("SELECT * FROM download_collection_tracks WHERE collectionId = :id ORDER BY position ASC")
    suspend fun getCollectionTracks(id: String): List<DownloadCollectionTrackEntity>

    @Query("SELECT * FROM download_collection_tracks WHERE trackKey = :trackKey")
    suspend fun getMembershipsForTrack(trackKey: String): List<DownloadCollectionTrackEntity>

    @Query(
        """
        SELECT c.* FROM download_collections c
        INNER JOIN download_collection_tracks t ON c.id = t.collectionId
        WHERE t.trackKey = :trackKey
        ORDER BY c.title ASC
        """,
    )
    suspend fun getCollectionsForTrack(trackKey: String): List<DownloadCollectionEntity>

    @Query(
        """
        SELECT d.* FROM downloads d
        INNER JOIN download_collection_tracks t ON d.trackKey = t.trackKey
        WHERE t.collectionId = :id
          AND d.state NOT IN ('Pending', 'Unsupported')
        ORDER BY t.position ASC
        """,
    )
    suspend fun getVisibleCollectionDownloads(id: String): List<DownloadEntity>

    @Upsert
    suspend fun upsertCollectionTracks(tracks: List<DownloadCollectionTrackEntity>)

    @Query("DELETE FROM download_collection_tracks WHERE collectionId = :id AND trackKey = :trackKey")
    suspend fun deleteCollectionTrack(id: String, trackKey: String)

    @Query("DELETE FROM download_collection_tracks WHERE collectionId = :id")
    suspend fun deleteCollectionTracks(id: String)

    @Query("SELECT COUNT(*) FROM download_collection_tracks WHERE trackKey = :trackKey")
    suspend fun countMemberships(trackKey: String): Int

    @Query(
        """
        SELECT trackKey FROM downloads
        WHERE manual = 0
          AND trackKey NOT IN (SELECT trackKey FROM download_collection_tracks)
        """,
    )
    suspend fun getUnreferencedTrackKeys(): List<String>
}
