package com.psycode.spotiflac.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.psycode.spotiflac.domain.model.DownloadStatus

@Entity(tableName = "download_tasks")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val size: Long,
    val progress: Int,
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val contentUri: String? = null,
    val torrentTitle: String,
    val torrentFilePath: String,
    val innerPath: String,
    val saveOption: String,
    val folderUri: String? = null,
    val speedBytesPerSec: Long = 0L,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
