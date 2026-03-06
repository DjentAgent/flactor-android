package com.psycode.spotiflac.domain.usecase.download
import com.psycode.spotiflac.domain.repository.DownloadRepository
import javax.inject.Inject







class DeleteGroupUseCase @Inject constructor(
    private val repo: DownloadRepository
) {
    suspend operator fun invoke(topicId: Int, alsoDeleteLocalFiles: Boolean = false) {
        repo.deleteGroup(topicId, alsoDeleteLocalFiles)
    }
}



