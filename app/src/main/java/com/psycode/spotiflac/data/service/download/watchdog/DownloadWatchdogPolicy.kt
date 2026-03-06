package com.psycode.spotiflac.data.service.download.watchdog

import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.domain.model.DownloadStatus

internal fun hasActiveTransfers(snapshot: List<DownloadEntity>): Boolean =
    snapshot.any { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
