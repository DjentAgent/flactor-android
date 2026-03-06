package com.psycode.spotiflac.data.service.download.orchestration

import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.domain.model.DownloadStatus

internal fun canPause(status: DownloadStatus): Boolean =
    status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED

internal fun canResume(status: DownloadStatus): Boolean =
    status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED

internal fun applyPauseTransition(current: DownloadEntity): DownloadEntity? =
    if (canPause(current.status)) {
        current.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0)
    } else {
        null
    }

internal fun applyResumeTransition(
    current: DownloadEntity,
    nowMs: Long
): DownloadEntity? = when (current.status) {
    DownloadStatus.PAUSED -> current.copy(
        status = DownloadStatus.QUEUED,
        speedBytesPerSec = 0,
        createdAt = nowMs
    )
    DownloadStatus.FAILED -> current.copy(
        status = DownloadStatus.QUEUED,
        speedBytesPerSec = 0,
        errorMessage = null,
        createdAt = nowMs
    )
    else -> null
}

internal fun applyCancelTransition(current: DownloadEntity): DownloadEntity =
    current.copy(status = DownloadStatus.CANCELED, speedBytesPerSec = 0)
