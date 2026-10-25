package net.pipebomb.android.playback.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.CatalogRepository
import net.pipebomb.android.data.SessionState
import net.pipebomb.android.data.download.DownloadCollectionEntity
import net.pipebomb.android.data.download.DownloadCollectionType
import net.pipebomb.android.data.download.DownloadRepository
import net.pipebomb.android.playback.PlaybackCache

@HiltWorker
class CollectionSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DownloadRepository,
    private val catalogRepository: CatalogRepository,
    private val authRepository: AuthRepository,
    private val coordinator: DownloadCoordinator,
    private val cache: PlaybackCache,
    private val downloadScheduler: DownloadScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = authRepository.session.first()
        val user = (session as? SessionState.Authenticated)?.user ?: return Result.success()
        val requestedId = inputData.getString(KEY_COLLECTION_ID)
        val collections = requestedId?.let { id ->
            repository.getCollection(id)?.let(::listOf).orEmpty()
        } ?: repository.getCollections()

        for (collection in collections) {
            if (collection.accountUuid != null && collection.accountUuid != user.uuid) continue
            syncCollection(collection, user.uuid, user.username)
        }
        return Result.success()
    }

    private suspend fun syncCollection(
        collection: DownloadCollectionEntity,
        accountUuid: String,
        accountName: String,
    ) {
        when (collection.collectionType) {
            DownloadCollectionType.Playlist -> syncPlaylist(collection, accountUuid, accountName)
            DownloadCollectionType.Album -> syncAlbum(collection, accountUuid, accountName)
        }
    }

    private suspend fun syncPlaylist(
        collection: DownloadCollectionEntity,
        accountUuid: String,
        accountName: String,
    ) {
        when (val result = catalogRepository.playlist(collection.sourceId, tracks = 0)) {
            is ApiResult.Failure -> handleFailure(collection, result.error)
            is ApiResult.Success -> {
                val playlist = result.value
                val revision = playlist.dateModified
                if (collection.remoteRevision != null && collection.remoteRevision == revision) {
                    repository.markCollectionSynced(collection.id, null)
                    if (repository.retryFailedMembers(collection.id) > 0) {
                        downloadScheduler.schedule()
                    }
                    return
                }
                when (val tracksResult = catalogRepository.allPlaylistTracks(collection.sourceId)) {
                    is ApiResult.Failure -> handleFailure(collection, tracksResult.error)
                    is ApiResult.Success -> applySnapshot(
                        collection = collection,
                        title = DisplayText.title(playlist),
                        artworkUrl = DisplayText.artwork(playlist)?.url,
                        ownerName = DisplayText.ownerName(playlist),
                        accountUuid = accountUuid,
                        accountName = accountName,
                        revision = revision,
                        tracks = tracksResult.value.map { it.track },
                    )
                }
            }
        }
    }

    private suspend fun syncAlbum(
        collection: DownloadCollectionEntity,
        accountUuid: String,
        accountName: String,
    ) {
        when (val result = catalogRepository.album(collection.sourceId)) {
            is ApiResult.Failure -> handleFailure(collection, result.error)
            is ApiResult.Success -> {
                val album = result.value
                applySnapshot(
                    collection = collection,
                    title = DisplayText.title(album),
                    artworkUrl = DisplayText.artwork(album)?.url,
                    ownerName = DisplayText.artists(album),
                    accountUuid = accountUuid,
                    accountName = accountName,
                    revision = null,
                    tracks = album.tracks.orEmpty(),
                )
            }
        }
    }

    private suspend fun applySnapshot(
        collection: DownloadCollectionEntity,
        title: String,
        artworkUrl: String?,
        ownerName: String?,
        accountUuid: String,
        accountName: String,
        revision: String?,
        tracks: List<Track>,
    ) {
        val result = repository.reconcileCollection(
            collectionId = collection.id,
            title = title,
            artworkUrl = artworkUrl,
            ownerName = ownerName,
            accountUuid = accountUuid,
            accountName = accountName,
            remoteRevision = revision,
            tracks = tracks,
        )
        result.releasedTrackKeys.forEach { key ->
            cache.removeTrack(key)
            coordinator.remove(key)
        }
        if (result.pendingCreated > 0) downloadScheduler.schedule()
    }

    private suspend fun handleFailure(
        collection: DownloadCollectionEntity,
        error: ApiError,
    ) {
        if (error is ApiError.NotFound) {
            val released = repository.unsubscribeCollection(collection.id)
            released.forEach { key ->
                cache.removeTrack(key)
                coordinator.remove(key)
            }
            return
        }
        if (error is ApiError.Unauthorized) return
        repository.markCollectionError(collection.id, error.userMessage())
    }

    companion object {
        const val KEY_COLLECTION_ID = "collection_id"
    }
}
