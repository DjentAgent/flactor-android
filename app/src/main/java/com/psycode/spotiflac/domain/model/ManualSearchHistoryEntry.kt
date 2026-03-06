package com.psycode.spotiflac.domain.model

data class ManualSearchHistoryEntry(
    val artist: String,
    val title: String,
    val isLossless: Boolean
) {
    fun displayTitle(): String = if (title.isBlank()) artist else "$artist - $title"
}

