package net.pipebomb.android.playback.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun schedulePeriodic() {
        val data = Data.Builder()
            .putBoolean(CollectionSyncWorker.KEY_RETRY_FAILED, true)
            .build()
        val request = PeriodicWorkRequestBuilder<CollectionSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow(collectionId: String? = null) {
        val data = Data.Builder().apply {
            collectionId?.let { putString(CollectionSyncWorker.KEY_COLLECTION_ID, it) }
        }.build()
        val request = OneTimeWorkRequestBuilder<CollectionSyncWorker>()
            .setConstraints(networkConstraints())
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val PERIODIC_WORK_NAME = "collection-sync-periodic"
        const val ONE_TIME_WORK_NAME = "collection-sync-now"
    }
}
