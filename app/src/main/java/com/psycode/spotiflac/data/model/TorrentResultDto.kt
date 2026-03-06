package com.psycode.spotiflac.data.model

import com.google.gson.annotations.SerializedName

data class TorrentResultDto(
    @SerializedName("title")    val title: String,
    @SerializedName("url")      val url:   String,
    @SerializedName("size")     val size:  String,
    @SerializedName("seeders")  val seeders: Int,
    @SerializedName("leechers") val leechers: Int
)