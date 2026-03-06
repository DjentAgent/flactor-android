package com.psycode.spotiflac.domain.usecase.track
import com.psycode.spotiflac.domain.model.TrackDetail
import com.psycode.spotiflac.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTrackDetailUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(trackId: String): Flow<TrackDetail> =
        trackRepository.getTrackById(trackId)
}


