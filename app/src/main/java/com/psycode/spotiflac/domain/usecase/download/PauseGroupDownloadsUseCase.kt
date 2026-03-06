package com.psycode.spotiflac.domain.usecase.download

import com.psycode.spotiflac.domain.repository.DownloadRepository
import javax.inject.Inject

class PauseGroupDownloadsUseCase @Inject constructor(
    private val repo: DownloadRepository
) {
    suspend operator fun invoke(topicId: Int) {
        repo.pauseGroup(topicId)
    }
}
