package com.psycode.spotiflac.domain.usecase.download
import com.psycode.spotiflac.domain.repository.DownloadRepository
import javax.inject.Inject




class ResumeDownloadUseCase @Inject constructor(
    private val repo: DownloadRepository
) {
    suspend operator fun invoke(taskId: String) {
        repo.resumeDownload(taskId)
    }
}


