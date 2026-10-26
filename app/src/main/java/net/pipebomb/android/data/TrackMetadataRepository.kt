package net.pipebomb.android.data

import javax.inject.Inject
import javax.inject.Singleton
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.data.download.DownloadRepository

@Singleton
class TrackMetadataRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val connectivity: ConnectivityStatus,
) {
    suspend fun local(keys: Collection<TrackKey>): Map<TrackKey, Track> =
        downloadRepository.localMetadata(keys)

    suspend fun refresh(keys: Collection<TrackKey>): Map<TrackKey, Track> {
        if (keys.isEmpty() || !connectivity.isOnline.value) return emptyMap()
        return (catalogRepository.tracks(keys.toList()) as? ApiResult.Success)
            ?.value
            ?.associateBy { it.key }
            .orEmpty()
    }

    suspend fun resolve(key: TrackKey): Track? {
        if (connectivity.isOnline.value) {
            val remote = (catalogRepository.tracks(listOf(key)) as? ApiResult.Success)
                ?.value
                ?.firstOrNull()
            if (remote != null) return remote
        }
        return local(listOf(key))[key]
    }
}
