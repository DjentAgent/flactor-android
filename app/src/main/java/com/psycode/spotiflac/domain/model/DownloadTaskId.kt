package com.psycode.spotiflac.domain.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun buildDownloadTaskId(topicId: Int, innerPath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(innerPath.toByteArray(StandardCharsets.UTF_8))
    val hex = bytes.joinToString(separator = "") { "%02x".format(it) }
    return "${topicId}_${hex.take(16)}"
}
