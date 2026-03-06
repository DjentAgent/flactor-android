package com.psycode.spotiflac.data.service.download.session

import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class DownloadSessionAlertMonitor(
    private val session: SessionManager,
    private val onSessionRecoveryRequested: (String) -> Unit
) {
    private val listener = object : AlertListener {
        override fun types(): IntArray = trackedTypes

        override fun alert(alert: Alert<*>) {
            when (alert.type()) {
                AlertType.SESSION_ERROR,
                AlertType.UDP_ERROR -> triggerRecovery("critical:${alert.type()}")
                AlertType.LISTEN_FAILED -> {
                    if (recordAndExceeds(listenFailedEvents, LISTEN_FAILED_WINDOW_MS, LISTEN_FAILED_THRESHOLD)) {
                        triggerRecovery("listen_failed_burst")
                    }
                }
                AlertType.TRACKER_ERROR -> {
                    if (recordAndExceeds(trackerErrorEvents, TRACKER_ERROR_WINDOW_MS, TRACKER_ERROR_THRESHOLD)) {
                        triggerRecovery("tracker_error_burst")
                    }
                }
                AlertType.PORTMAP_ERROR -> {
                    // UPnP/NAT-PMP mapping frequently fails on mobile/Wi-Fi routers and is not fatal.
                    // Keep trace logging, but do not trigger session recovery.
                }
                AlertType.ALERTS_DROPPED -> DownloadLog.w("Alert queue dropped events; increase alert queue size if repeated")
                else -> Unit
            }
            DownloadLog.t(
                scope = "SessionAlert",
                message = "type=${alert.type()} what=${alert.what()} msg=${alert.message()}"
            )
        }
    }

    private val lastRecoveryAtMs = AtomicLong(0L)
    private val lock = Any()
    private val listenFailedEvents = ArrayDeque<Long>()
    private val trackerErrorEvents = ArrayDeque<Long>()

    fun start() {
        session.addListener(listener)
        DownloadLog.d("Session alert monitor started")
    }

    fun stop() {
        runCatching { session.removeListener(listener) }
        DownloadLog.d("Session alert monitor stopped")
    }

    private fun triggerRecovery(reason: String) {
        val now = System.currentTimeMillis()
        val last = lastRecoveryAtMs.get()
        if (now - last < RECOVERY_COOLDOWN_MS) return
        if (!lastRecoveryAtMs.compareAndSet(last, now)) return
        onSessionRecoveryRequested(reason)
    }

    private fun recordAndExceeds(events: ArrayDeque<Long>, windowMs: Long, threshold: Int): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            events.addLast(now)
            while (events.isNotEmpty() && now - events.first() > windowMs) {
                events.removeFirst()
            }
            return events.size >= threshold
        }
    }
}

private val trackedTypes = intArrayOf(
    AlertType.SESSION_ERROR.swig(),
    AlertType.LISTEN_FAILED.swig(),
    AlertType.PORTMAP_ERROR.swig(),
    AlertType.UDP_ERROR.swig(),
    AlertType.TRACKER_ERROR.swig(),
    AlertType.ALERTS_DROPPED.swig()
)

private const val RECOVERY_COOLDOWN_MS = 15_000L
private const val LISTEN_FAILED_WINDOW_MS = 30_000L
private const val LISTEN_FAILED_THRESHOLD = 3
private const val TRACKER_ERROR_WINDOW_MS = 30_000L
private const val TRACKER_ERROR_THRESHOLD = 10
