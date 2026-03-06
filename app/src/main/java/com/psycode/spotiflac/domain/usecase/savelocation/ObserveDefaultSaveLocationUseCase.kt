package com.psycode.spotiflac.domain.usecase.savelocation
import com.psycode.spotiflac.domain.model.DefaultSaveLocation
import com.psycode.spotiflac.domain.repository.SaveLocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDefaultSaveLocationUseCase @Inject constructor(
    private val repository: SaveLocationRepository
) {
    operator fun invoke(): Flow<DefaultSaveLocation> = repository.observeDefaultSaveLocation()
}



