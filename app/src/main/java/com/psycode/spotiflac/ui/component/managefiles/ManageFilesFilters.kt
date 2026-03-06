package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.DownloadStatus

enum class ManageFilesFilter {
    ALL,
    DOWNLOADABLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

data class ManageFilesFilterStats(
    val all: Int,
    val downloadable: Int,
    val downloading: Int,
    val downloaded: Int,
    val failed: Int
)

fun recommendedManageFilesFilter(stats: ManageFilesFilterStats): ManageFilesFilter = when {
    stats.failed > 0 -> ManageFilesFilter.FAILED
    stats.downloading > 0 -> ManageFilesFilter.DOWNLOADING
    stats.downloadable > 0 -> ManageFilesFilter.DOWNLOADABLE
    else -> ManageFilesFilter.ALL
}

fun buildManageFilesFilterStats(prep: PreparedFilesDl): ManageFilesFilterStats {
    val statuses = prep.ordered.map { it.status }
    return ManageFilesFilterStats(
        all = prep.ordered.size,
        downloadable = prep.selectableIndices.size,
        downloading = statuses.count {
            it in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)
        },
        downloaded = prep.removableCompletedIndices.size,
        failed = statuses.count {
            it == DownloadStatus.FAILED || it == DownloadStatus.CANCELED
        }
    )
}

fun manageFilesFilterMatches(
    ui: UiFileDl,
    filter: ManageFilesFilter,
    queryNorm: String
): Boolean {
    val statusMatches = when (filter) {
        ManageFilesFilter.ALL -> true
        ManageFilesFilter.DOWNLOADABLE -> ui.status !in setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
            DownloadStatus.COMPLETED
        )
        ManageFilesFilter.DOWNLOADING -> ui.status in setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED
        )
        ManageFilesFilter.DOWNLOADED -> ui.status == DownloadStatus.COMPLETED
        ManageFilesFilter.FAILED -> ui.status == DownloadStatus.FAILED || ui.status == DownloadStatus.CANCELED
    }
    if (!statusMatches) return false
    if (queryNorm.isBlank()) return true
    return ui.normalizedName.contains(queryNorm) || ui.normalizedPath.contains(queryNorm)
}
