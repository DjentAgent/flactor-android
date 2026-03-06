package com.psycode.spotiflac.data.service.download.runtime

import android.content.Context
import android.os.PowerManager
import android.net.wifi.WifiManager
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.sync.Semaphore

data class DownloadRuntimeInfra(
    val semaphore: Semaphore,
    val wakeLock: PowerManager.WakeLock,
    val wifiLock: WifiManager.WifiLock?
)

class DownloadRuntimeInfraFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun create(maxParallelDownloads: Int): DownloadRuntimeInfra {
        val parallelism = maxParallelDownloads.coerceAtLeast(1)
        if (parallelism != maxParallelDownloads) {
            DownloadLog.w("Invalid maxParallelDownloads=$maxParallelDownloads, clamped to $parallelism")
        }
        val semaphore = Semaphore(parallelism)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpotiFlac::DownloadWakeLock")
        @Suppress("DEPRECATION")
        val wifiLock = runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SpotiFlac::DownloadWifiLock").apply {
                setReferenceCounted(false)
            }
        }.onFailure {
            DownloadLog.w("WifiLock is unavailable; background Wi-Fi stability may degrade")
        }.getOrNull()
        return DownloadRuntimeInfra(
            semaphore = semaphore,
            wakeLock = wakeLock,
            wifiLock = wifiLock
        )
    }
}




