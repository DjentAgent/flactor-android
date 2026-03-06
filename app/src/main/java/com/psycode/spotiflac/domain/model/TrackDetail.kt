package com.psycode.spotiflac.domain.model

data class TrackDetail(
    val id: String,
    val title: String,
    val artist: String,
    val albumName: String,
    val albumCoverUrl: String,
    val durationMs: Int,
    val popularity: Int,
    val previewUrl: String?
)