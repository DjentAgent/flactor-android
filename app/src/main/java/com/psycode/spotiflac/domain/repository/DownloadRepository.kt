package com.psycode.spotiflac.domain.repository

import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import kotlinx.coroutines.flow.Flow




interface DownloadRepository {

    









    suspend fun enqueueDownloads(
        topicId: Int,
        files: List<TorrentFile>,
        saveOption: SaveOption,
        folderUri: String?,
        torrentTitle: String,
    )

    
    fun observeDownloads(): Flow<List<DownloadTask>>

    
    suspend fun pauseDownload(taskId: String)

    
    suspend fun resumeDownload(taskId: String)

    suspend fun pauseGroup(topicId: Int)

    suspend fun resumeGroup(topicId: Int)

    
    suspend fun cancelDownload(taskId: String)

    suspend fun removeDownload(taskId: String)

    





    suspend fun deleteGroup(topicId: Int, alsoDeleteLocalFiles: Boolean = false)
}

