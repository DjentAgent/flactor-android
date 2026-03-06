package com.psycode.spotiflac.domain.repository

import androidx.paging.PagingData
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.domain.model.TrackDetail
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    
    fun getSavedTracks(): Flow<PagingData<Track>>
    fun searchTracks(query: String): Flow<PagingData<Track>>
    fun getTrackById(trackId: String): Flow<TrackDetail>
}

