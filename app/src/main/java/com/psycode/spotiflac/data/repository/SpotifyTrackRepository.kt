package com.psycode.spotiflac.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.psycode.spotiflac.data.network.BackendSpotifyApi
import com.psycode.spotiflac.data.paging.PublicSearchPagingSource
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.repository.AppModeRepository
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.domain.model.TrackDetail
import com.psycode.spotiflac.domain.repository.TrackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SpotifyTrackRepository @Inject constructor(
    private val backendApi: BackendSpotifyApi,
    private val appModeRepository: AppModeRepository
) : TrackRepository {

    private suspend fun currentMode(): AppMode =
        appModeRepository.observeMode().firstOrNull() ?: AppMode.Unselected

    override fun getSavedTracks(): Flow<PagingData<Track>> = flowOf(PagingData.empty())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun searchTracks(query: String): Flow<PagingData<Track>> =
        appModeRepository.observeMode().flatMapLatest { mode ->
            if (mode != AppMode.SpotifyPublic || query.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                    pagingSourceFactory = {
                        PublicSearchPagingSource(backendApi, query, pageSize = 20)
                    }
                ).flow
            }
        }

    override fun getTrackById(trackId: String): Flow<TrackDetail> = flow {
        val mode = currentMode()
        if (mode != AppMode.SpotifyPublic) return@flow

        val dto = backendApi.getTrackDetailPublic(trackId)
        emit(
            TrackDetail(
                id = dto.id,
                title = dto.title,
                artist = dto.artists.joinToString { it.name },
                albumName = dto.album.name,
                albumCoverUrl = dto.album.images.firstOrNull()?.url.orEmpty(),
                durationMs = dto.durationMs,
                popularity = dto.popularity,
                previewUrl = dto.previewUrl
            )
        )
    }
}
