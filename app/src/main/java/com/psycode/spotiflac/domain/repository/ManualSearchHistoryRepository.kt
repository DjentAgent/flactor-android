package com.psycode.spotiflac.domain.repository

import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import kotlinx.coroutines.flow.Flow

interface ManualSearchHistoryRepository {
    fun observeHistory(): Flow<List<ManualSearchHistoryEntry>>
    suspend fun save(entry: ManualSearchHistoryEntry)
    suspend fun remove(entry: ManualSearchHistoryEntry)
    suspend fun clear()
}
