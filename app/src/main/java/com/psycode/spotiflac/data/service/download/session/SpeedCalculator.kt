package com.psycode.spotiflac.data.service.download.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpeedCalculator(
    private val windowSize: Int = 10,
    private val minWindowMs: Long = MIN_SPEED_WINDOW_MS,
    private val maxDisplayBytesPerSec: Long = MAX_DISPLAY_SPEED_BYTES_PER_SEC
) {
    private val measurements = mutableListOf<Pair<Long, Long>>()
    private val lock = Mutex()

    suspend fun addMeasurement(timestamp: Long, totalBytes: Long): Long = lock.withLock {
        measurements.add(timestamp to totalBytes)
        if (measurements.size > windowSize) measurements.removeAt(0)
        return if (measurements.size >= 2) {
            val first = measurements.first()
            val last = measurements.last()
            val deltaMs = last.first - first.first
            if (deltaMs < minWindowMs) return@withLock 0L
            val deltaBytes = (last.second - first.second).coerceAtLeast(0L)
            val deltaTime = deltaMs / 1000.0
            if (deltaTime <= 0) return@withLock 0L
            (deltaBytes / deltaTime).toLong()
                .coerceAtLeast(0L)
                .coerceAtMost(maxDisplayBytesPerSec)
        } else 0L
    }

    suspend fun reset() = lock.withLock { measurements.clear() }
}

internal const val MIN_SPEED_WINDOW_MS = 1_500L
internal const val MAX_DISPLAY_SPEED_BYTES_PER_SEC = 80L * 1024L * 1024L




