package com.psycode.spotiflac.domain.usecase.torrent
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.repository.TorrentRepository
import javax.inject.Inject

class GetTorrentFilesUseCase @Inject constructor(
    private val repository: TorrentRepository
) {
    suspend operator fun invoke(topicId: Int): List<TorrentFile> =
        repository.getTorrentFiles(topicId)
}


