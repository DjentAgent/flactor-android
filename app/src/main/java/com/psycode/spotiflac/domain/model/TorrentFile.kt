package com.psycode.spotiflac.domain.model






data class TorrentFile(
    val name: String,
    val size: Long,
    val torrentFilePath: String,
    val innerPath: String
)
