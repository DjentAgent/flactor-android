package com.psycode.spotiflac.data.service.download.util

internal fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format("%.1f MB/s", mb) else String.format("%.0f KB/s", kb)
}

internal fun formatTime(seconds: Long): String =
    when {
        seconds < 60 -> "${seconds}с"
        seconds < 3600 -> "${seconds / 60}м ${seconds % 60}с"
        else -> "${seconds / 3600}ч ${(seconds % 3600) / 60}м"
    }





