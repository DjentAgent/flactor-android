package com.psycode.spotiflac.domain.repository

import com.psycode.spotiflac.domain.model.DefaultSaveLocation
import com.psycode.spotiflac.domain.model.SaveOption
import kotlinx.coroutines.flow.Flow

interface SaveLocationRepository {
    fun observeDefaultSaveLocation(): Flow<DefaultSaveLocation>

    suspend fun setDefaultSaveLocation(saveOption: SaveOption, customFolderUri: String?)
}

