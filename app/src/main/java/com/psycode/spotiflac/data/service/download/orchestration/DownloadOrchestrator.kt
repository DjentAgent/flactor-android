package com.psycode.spotiflac.data.service.download.orchestration

import android.net.wifi.WifiManager
import android.os.PowerManager
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.session.SpeedCalculator
import com.psycode.spotiflac.domain.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap

class DownloadOrchestrator(
    private val scope: CoroutineScope,
    private val semaphore: Semaphore,
    private val wakeLock: PowerManager.WakeLock,
    private val wifiLock: WifiManager.WifiLock?,
    private val speedCalculators: ConcurrentHashMap<String, SpeedCalculator>,
    private val taskProcessor: DownloadTaskProcessor,
    private val maxParallelDownloads: Int,
    private val maxParallelPerTorrent: Int,
    private val maxRetryAttempts: Int,
    private var onTaskSettled: suspend () -> Unit = {}
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val wakeLockGuard = Any()
    private val wifiLockGuard = Any()
    private val pausedSlotReservations = ConcurrentHashMap<String, Long>()
    private var lastNoSelectionWarnAtMs: Long = 0L

    fun launch(name: String, block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadLog.e("Coroutine $name failed", e)
            }
        }
        activeJobs[name]?.cancel()
        activeJobs[name] = job
        return job
    }

    suspend fun processTaskList(list: List<DownloadEntity>) {
        pruneExpiredReservations()
        DownloadLog.d("processTaskList total=${list.size} scheduled=${running.size}")
        val toProcess = selectTasksToSchedule(
            snapshot = list,
            scheduledTaskIds = running,
            maxParallelDownloads = maxParallelDownloads,
            maxParallelPerTorrent = maxParallelPerTorrent,
            reservedPausedTaskIds = pausedSlotReservations.keys
        ).filter { entity -> running.add(entity.id) }
        if (toProcess.isNotEmpty()) {
            DownloadLog.d("Scheduling tasks: ${toProcess.joinToString { "${it.id}:${it.status}" }}")
        }
        if (toProcess.isEmpty()) {
            val activeInSnapshot = list.filter {
                it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
            }
            if (activeInSnapshot.isNotEmpty() && running.isEmpty()) {
                    val now = System.currentTimeMillis()
                    if (now - lastNoSelectionWarnAtMs >= NO_SELECTION_WARN_COOLDOWN_MS) {
                        lastNoSelectionWarnAtMs = now
                        DownloadLog.d(
                            "SCHEDULER_NO_SELECTION active=${activeInSnapshot.joinToString { "${it.id}:${it.status}" }} " +
                                "reservedPaused=${pausedSlotReservations.keys.joinToString()}"
                        )
                    }
                }
        }
        if (toProcess.isNotEmpty()) {
            ensureWakeLockHeld()
            ensureWifiLockHeld()
        }

        toProcess.forEach { entity ->
            launch("task-${entity.id}") {
                try {
                    taskProcessor.runTaskWithRetry(entity, maxRetryAttempts, semaphore)
                } finally {
                    DownloadLog.d("Task ${entity.id} settled, cleaning runtime state")
                    running.remove(entity.id)
                    speedCalculators.remove(entity.id)
                    releaseWakeLockIfIdle()
                    releaseWifiLockIfIdle()
                    onTaskSettled()
                }
            }
        }
    }

    private fun ensureWakeLockHeld() {
        synchronized(wakeLockGuard) {
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
                DownloadLog.d("WakeLock acquired")
            }
        }
    }

    private fun releaseWakeLockIfIdle() {
        synchronized(wakeLockGuard) {
            if (running.isEmpty() && wakeLock.isHeld) {
                wakeLock.release()
                DownloadLog.d("WakeLock released")
            }
        }
    }

    private fun ensureWifiLockHeld() {
        val lock = wifiLock ?: return
        synchronized(wifiLockGuard) {
            if (!lock.isHeld) {
                lock.acquire()
                DownloadLog.d("WifiLock acquired")
            }
        }
    }

    private fun releaseWifiLockIfIdle() {
        val lock = wifiLock ?: return
        synchronized(wifiLockGuard) {
            if (running.isEmpty() && lock.isHeld) {
                lock.release()
                DownloadLog.d("WifiLock released")
            }
        }
    }

    fun runningTaskIds(): Set<String> = running

    fun hasRunningTasks(): Boolean = running.isNotEmpty()

    fun setOnTaskSettled(block: suspend () -> Unit) {
        onTaskSettled = block
    }

    fun cancelAllJobs() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun onUserPaused(taskId: String) {
        pausedSlotReservations[taskId] = System.currentTimeMillis() + USER_PAUSE_RESERVATION_MS
    }

    fun onUserResumed(taskId: String) {
        pausedSlotReservations.remove(taskId)
    }

    private fun pruneExpiredReservations() {
        val now = System.currentTimeMillis()
        pausedSlotReservations.entries.removeIf { it.value <= now }
    }
}

internal fun selectTasksToSchedule(
    snapshot: List<DownloadEntity>,
    scheduledTaskIds: Set<String>,
    maxParallelDownloads: Int,
    maxParallelPerTorrent: Int = 1,
    reservedPausedTaskIds: Set<String> = emptySet()
): List<DownloadEntity> {
    if (snapshot.isEmpty()) return emptyList()
    val parallelism = maxParallelDownloads.coerceAtLeast(1)
    val perTorrentLimit = maxParallelPerTorrent.coerceAtLeast(1).coerceAtMost(parallelism)
    val scheduled = scheduledTaskIds.toSet()
    val snapshotById = snapshot.associateBy { it.id }

    val reservedPausedCount = snapshot.count { entity ->
        entity.id in reservedPausedTaskIds &&
            entity.id !in scheduled &&
            entity.status == DownloadStatus.PAUSED
    }

    var slotsLeft = (parallelism - scheduled.size - reservedPausedCount).coerceAtLeast(0)

    val activeTopicCounts = scheduled.asSequence()
        .mapNotNull { taskId -> extractTopicKey(snapshotById[taskId]) ?: extractTopicKey(taskId) }
        .groupingBy { it }
        .eachCount()
        .toMutableMap()

    fun takeByTopic(candidates: List<DownloadEntity>, maxCount: Int): List<DownloadEntity> {
        if (maxCount <= 0) return emptyList()
        val selected = mutableListOf<DownloadEntity>()
        for (entity in candidates) {
            if (selected.size >= maxCount) break
            val topic = extractTopicKey(entity)
            if (topic != null && (activeTopicCounts[topic] ?: 0) >= perTorrentLimit) continue
            if (topic != null) {
                activeTopicCounts[topic] = (activeTopicCounts[topic] ?: 0) + 1
            }
            selected += entity
        }
        return selected
    }

    val runningCandidates = takeByTopic(
        candidates = snapshot
            .asSequence()
            .filter { it.status == DownloadStatus.RUNNING && it.id !in scheduled }
            .sortedByDescending { it.createdAt }
            .toList(),
        maxCount = slotsLeft
    )

    slotsLeft = (slotsLeft - runningCandidates.size).coerceAtLeast(0)

    val queuedCandidates = takeByTopic(
        candidates = snapshot
            .asSequence()
            .filter { it.status == DownloadStatus.QUEUED && it.id !in scheduled }
            .sortedByDescending { it.createdAt }
            .toList(),
        maxCount = slotsLeft
    )

    return runningCandidates + queuedCandidates
}

private fun extractTopicKey(entity: DownloadEntity?): String? {
    if (entity == null) return null
    val path = entity.torrentFilePath.trim()
    if (path.isNotEmpty()) return path.replace('\\', '/').lowercase()
    return extractTopicKey(entity.id)
}

private fun extractTopicKey(taskId: String): String? {
    val separator = taskId.indexOf('_')
    if (separator <= 0) return null
    return taskId.substring(0, separator)
}

private const val USER_PAUSE_RESERVATION_MS = 4_000L
private const val NO_SELECTION_WARN_COOLDOWN_MS = 30_000L




