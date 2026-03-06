package com.psycode.spotiflac.domain.usecase.download
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject




class ObserveDownloadsUseCase @Inject constructor(
    private val repo: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadTask>> =
        repo.observeDownloads()
}


