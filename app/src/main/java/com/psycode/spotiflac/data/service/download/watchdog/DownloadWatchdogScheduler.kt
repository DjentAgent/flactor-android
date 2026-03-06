package com.psycode.spotiflac.data.service.download.watchdog

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadWatchdogScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    private val scheduled = AtomicBoolean(false)

    fun ensureScheduled() {
        if (!scheduled.compareAndSet(false, true)) return
        val request = PeriodicWorkRequestBuilder<DownloadWatchdogWorker>(
            WATCHDOG_REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            DOWNLOAD_WATCHDOG_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        DownloadLog.t(scope = "Watchdog", message = "scheduled periodic watchdog")
    }

    fun cancel() {
        if (!scheduled.compareAndSet(true, false)) return
        workManager.cancelUniqueWork(DOWNLOAD_WATCHDOG_PERIODIC_WORK)
        DownloadLog.t(scope = "Watchdog", message = "canceled periodic watchdog")
    }

    fun sync(snapshot: List<DownloadEntity>) {
        if (hasActiveTransfers(snapshot)) {
            ensureScheduled()
        } else {
            cancel()
        }
    }
    companion object {
        private const val WATCHDOG_REPEAT_INTERVAL_MINUTES = 15L
    }
}
