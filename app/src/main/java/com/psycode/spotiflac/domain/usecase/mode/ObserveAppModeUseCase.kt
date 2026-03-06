package com.psycode.spotiflac.domain.usecase.mode
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.repository.AppModeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAppModeUseCase @Inject constructor(
    private val appModeRepository: AppModeRepository
) {
    operator fun invoke(): Flow<AppMode> =
        appModeRepository.observeMode()
}


