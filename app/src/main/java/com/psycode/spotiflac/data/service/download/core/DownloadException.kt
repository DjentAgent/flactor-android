package com.psycode.spotiflac.data.service.download.core

sealed class DownloadException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class TorrentNotFound(path: String) : DownloadException("Torrent file not found: $path")
    class FileNotFound(fileName: String) : DownloadException("File not found in torrent: $fileName")
    class SessionError(cause: Throwable) :
        DownloadException("Torrent session error: ${cause.message}", cause)

    class StorageError(cause: Throwable) :
        DownloadException("Storage error: ${cause.message}", cause)

    class ValidationError(message: String) : DownloadException(message)
}




