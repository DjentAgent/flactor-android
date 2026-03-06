package com.psycode.spotiflac.domain.usecase.track
import androidx.paging.PagingData
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(query: String): Flow<PagingData<Track>> =
        trackRepository.searchTracks(query)
}


