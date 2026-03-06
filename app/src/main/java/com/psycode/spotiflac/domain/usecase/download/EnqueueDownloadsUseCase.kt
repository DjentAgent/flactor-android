package com.psycode.spotiflac.domain.usecase.download
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.repository.DownloadRepository
import javax.inject.Inject





class EnqueueDownloadsUseCase @Inject constructor(
    private val repo: DownloadRepository
) {
    





    suspend operator fun invoke(
        topicId: Int,
        files: List<TorrentFile>,
        saveOption: SaveOption,
        folderUri: String?,
        torrentTitle: String
    ) {
        repo.enqueueDownloads(topicId, files, saveOption, folderUri, torrentTitle)
    }
}



