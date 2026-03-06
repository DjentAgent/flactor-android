package com.psycode.spotiflac.domain.repository

import com.psycode.spotiflac.domain.mode.AppMode
import kotlinx.coroutines.flow.Flow

interface AppModeRepository {
    suspend fun setMode(mode: AppMode)

    fun observeMode(): Flow<AppMode>

    suspend fun clearMode()
}
