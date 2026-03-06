package com.psycode.spotiflac.domain.usecase.track
import androidx.paging.PagingData
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSavedTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(): Flow<PagingData<Track>> =
        trackRepository.getSavedTracks()
}


