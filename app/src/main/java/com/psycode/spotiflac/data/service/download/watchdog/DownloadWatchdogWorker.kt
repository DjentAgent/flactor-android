package com.psycode.spotiflac.data.service.download.watchdog

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.service.DownloadServiceRouter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DownloadWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: DownloadDao,
    private val scheduler: DownloadWatchdogScheduler
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val snapshot = runCatching { dao.getAllSnapshot() }
            .onFailure { DownloadLog.e("Watchdog failed to read snapshot", it) }
            .getOrNull() ?: return Result.retry()

        val hasActive = hasActiveTransfers(snapshot)
        if (hasActive) {
            DownloadLog.d("Watchdog detected active transfers; ensuring DownloadService is running")
            DownloadServiceRouter.Companion.ensureStarted(applicationContext)
        } else {
            scheduler.cancel()
            DownloadLog.t(scope = "Watchdog", message = "no active transfers, skip service start")
        }
        return Result.success()
    }
}
