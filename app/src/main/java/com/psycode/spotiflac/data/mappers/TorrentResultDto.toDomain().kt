package com.psycode.spotiflac.data.mappers

import android.net.Uri
import com.psycode.spotiflac.data.model.TorrentResultDto
import com.psycode.spotiflac.domain.model.TorrentResult

fun TorrentResultDto.toDomain(): TorrentResult {
    val topicId = Uri
        .parse(url)
        .getQueryParameter("t")
        ?.toIntOrNull()
        ?: throw IllegalArgumentException("Cannot parse topicId from URL: $url")

    return TorrentResult(
        title    = title,
        url      = url,
        size     = size,
        seeders  = seeders,
        leechers = leechers,
        topicId  = topicId
    )
}