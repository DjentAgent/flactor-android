package com.psycode.spotiflac.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

fun persistTreePermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    try {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    } catch (_: SecurityException) {
    }
}

fun isWritableTreeUriAccessible(context: Context, uriString: String?): Boolean {
    val parsedUri = uriString?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { Uri.parse(raw) }.getOrNull()
    } ?: return false

    val hasPersistedPermission = context.contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == parsedUri && permission.isReadPermission && permission.isWritePermission
    }
    if (!hasPersistedPermission) return false

    val tree = DocumentFile.fromTreeUri(context, parsedUri) ?: return false
    return tree.isDirectory && tree.canWrite()
}

fun formatTreeUriForUi(uriString: String?): String {
    val uri = uriString?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { Uri.parse(raw) }.getOrNull()
    } ?: return ""
    val fromPath = uri.path?.substringAfterLast(':', "").orEmpty()
    return fromPath.ifBlank { uri.toString() }
}

