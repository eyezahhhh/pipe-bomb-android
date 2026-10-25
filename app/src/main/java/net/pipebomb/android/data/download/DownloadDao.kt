package net.pipebomb.android.data.download

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY requestedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackKey = :trackKey")
    fun observe(trackKey: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE trackKey = :trackKey")
    suspend fun get(trackKey: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY requestedAt ASC")
    suspend fun getAll(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE trackKey = :trackKey")
    suspend fun delete(trackKey: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads")
    suspend fun totalBytes(): Long
}
