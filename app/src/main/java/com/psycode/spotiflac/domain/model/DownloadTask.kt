package com.psycode.spotiflac.domain.model














data class DownloadTask(
    val id: String,
    val fileName: String,
    val size: Long,
    val progress: Int,
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val contentUri: String? = null,
    val torrentTitle: String,
    val speedBytesPerSec: Long = 0,
    val createdAt: Long
)


