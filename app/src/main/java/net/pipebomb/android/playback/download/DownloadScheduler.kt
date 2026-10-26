package net.pipebomb.android.playback.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import net.pipebomb.android.core.datastore.AppSettingsStore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: AppSettingsStore,
) {
    suspend fun schedule() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    suspend fun ensureScheduled() {
        enqueue(ExistingWorkPolicy.KEEP)
    }

    suspend fun reschedule() {
        cancel()
        schedule()
    }

    fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<DownloadWatchdogWorker>(
            WATCHDOG_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCHDOG_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private suspend fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(requiredNetworkType())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }

    private suspend fun requiredNetworkType(): NetworkType =
        if (settings.allowMobileDataDownloads.first()) {
            NetworkType.CONNECTED
        } else {
            NetworkType.UNMETERED
        }

    companion object {
        const val WORK_NAME = "download-queue"
        const val WATCHDOG_WORK_NAME = "download-watchdog"
        private const val WATCHDOG_INTERVAL_MINUTES = 15L
    }
}
