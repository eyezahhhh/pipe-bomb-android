package net.pipebomb.android.data.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "download_collections")
data class DownloadCollectionEntity(
    @PrimaryKey val id: String,
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
    val lastSyncedAt: Long? = null,
    val lastError: String? = null,
) {
    val collectionType: DownloadCollectionType
        get() = DownloadCollectionType.fromStorage(type)
}

enum class DownloadCollectionType(val storageValue: String) {
    Playlist("playlist"),
    Album("album"),
    ;

    fun collectionId(sourceId: String): String = "$storageValue:$sourceId"

    companion object {
        fun fromStorage(value: String): DownloadCollectionType =
            entries.firstOrNull { it.storageValue == value } ?: Playlist

        fun idFor(type: DownloadCollectionType, sourceId: String): String =
            type.collectionId(sourceId)
    }
}

@Entity(
    tableName = "download_collection_tracks",
    primaryKeys = ["collectionId", "trackKey"],
    indices = [
        Index("trackKey"),
        Index("collectionId"),
    ],
)
data class DownloadCollectionTrackEntity(
    val collectionId: String,
    val trackKey: String,
    val position: Int,
    val addedAt: Long,
)

data class DownloadCollectionItem(
    val entity: DownloadCollectionEntity,
    val memberCount: Int,
    val downloadedCount: Int,
) {
    val id: String get() = entity.id
    val type: DownloadCollectionType get() = entity.collectionType
    val sourceId: String get() = entity.sourceId
    val title: String get() = entity.title
    val artworkUrl: String? get() = entity.artworkUrl
}

data class DownloadCollectionDraft(
    val type: DownloadCollectionType,
    val sourceId: String,
    val title: String,
    val artworkUrl: String?,
    val ownerName: String?,
)
