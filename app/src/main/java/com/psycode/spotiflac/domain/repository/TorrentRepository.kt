package com.psycode.spotiflac.domain.repository

import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.TorrentResult
import kotlinx.coroutines.flow.Flow

interface TorrentRepository {
    fun searchTorrents(query: String, track: String, lossless: Boolean): Flow<List<TorrentResult>>
    suspend fun completeCaptcha(sessionId: String, solution: String)
    suspend fun getTorrentFiles(topicId: Int): List<TorrentFile>
}
