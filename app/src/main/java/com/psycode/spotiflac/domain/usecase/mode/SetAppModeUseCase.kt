package com.psycode.spotiflac.domain.usecase.mode
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.repository.AppModeRepository
import javax.inject.Inject

class SetAppModeUseCase @Inject constructor(
    private val appModeRepository: AppModeRepository
) {
    suspend operator fun invoke(mode: AppMode) {
        appModeRepository.setMode(mode)
    }
}


