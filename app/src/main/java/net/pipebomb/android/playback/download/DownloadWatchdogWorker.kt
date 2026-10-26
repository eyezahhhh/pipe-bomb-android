package net.pipebomb.android.playback.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.pipebomb.android.data.download.DownloadRepository

@HiltWorker
class DownloadWatchdogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DownloadRepository,
    private val scheduler: DownloadScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (repository.hasIncompleteDownloads()) {
            scheduler.ensureScheduled()
        }
        return Result.success()
    }
}
