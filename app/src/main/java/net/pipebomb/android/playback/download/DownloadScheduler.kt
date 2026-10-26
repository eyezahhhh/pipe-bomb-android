package net.pipebomb.android.playback.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import net.pipebomb.android.core.datastore.AppSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: AppSettingsStore,
) {
    suspend fun schedule() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(requiredNetworkType())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    suspend fun reschedule() {
        cancel()
        schedule()
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private suspend fun requiredNetworkType(): NetworkType =
        if (settings.allowMobileDataDownloads.first()) {
            NetworkType.CONNECTED
        } else {
            NetworkType.UNMETERED
        }

    companion object {
        const val WORK_NAME = "download-queue"
    }
}
