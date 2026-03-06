package com.psycode.spotiflac.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable




@Serializable
public data class TorrentResult(
    
    @SerialName("title")
    val title: String,

    
    @SerialName("url")
    val url: String,

    
    @SerialName("size")
    val size: String,

    
    @SerialName("seeders")
    val seeders: Int,

    
    @SerialName("leechers")
    val leechers: Int,

    
    val topicId: Int
)
