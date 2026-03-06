package com.psycode.spotiflac.data.service.download.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.psycode.spotiflac.data.service.download.core.DownloadException
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@ServiceScoped
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val ILLEGAL = Regex("""[\\/:*?"<>|]""")

    private fun sanitize(name: String): String =
        name.replace(ILLEGAL, "_").trim().ifBlank { "untitled" }

    private fun splitBaseExt(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot + 1)
        else name to ""
    }

    private fun findUniqueDocName(parent: DocumentFile, base: String, ext: String): String {
        var idx = 1
        var candidate = if (ext.isEmpty()) base else "$base.$ext"
        while (parent.findFile(candidate) != null) {
            idx++
            candidate = if (ext.isEmpty()) "$base ($idx)" else "$base ($idx).$ext"
        }
        return candidate
    }

    suspend fun saveToMediaStore(src: File, name: String): Uri = withContext(Dispatchers.IO) {
        var createdUri: Uri? = null
        try {
            val resolver = context.contentResolver
            val displayName = sanitize(name.ifBlank { src.name })
            val mimeType = getMimeType(displayName)
            val isAudio = mimeType.startsWith("audio/")
            val collection = if (isAudio) {
                MediaStore.Audio.Media
                    .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads
                    .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            if (!isAudio) {
                DownloadLog.w(
                    "saveToMediaStore fallback to Downloads for non-audio file name='$displayName' mime=$mimeType"
                )
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (isAudio) {
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                }
            }
            val uri = resolver.insert(collection, values)
                ?: throw DownloadException.StorageError(RuntimeException("Failed to create MediaStore entry"))
            createdUri = uri

            resolver.openOutputStream(uri)?.use { os ->
                src.inputStream().use { input -> input.copyTo(os) }
            } ?: throw DownloadException.StorageError(
                RuntimeException("Cannot open output stream for $uri")
            )

            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, updateValues, null, null)
            uri
        } catch (e: DownloadException) {
            createdUri?.let { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            throw e
        } catch (e: Exception) {
            createdUri?.let { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            throw DownloadException.StorageError(e)
        }
    }

    suspend fun saveToCustomFolder(src: File, name: String, folderUriString: String?): Uri =
        withContext(Dispatchers.IO) {
            var createdDocument: DocumentFile? = null
            try {
                val resolver = context.contentResolver
                val treeUri = Uri.parse(
                    folderUriString ?: throw DownloadException.ValidationError("No folderUri")
                )

                val dir = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw DownloadException.ValidationError("Not a valid tree URI: $treeUri")
                if (!dir.isDirectory) {
                    throw DownloadException.ValidationError("Picked URI is not a directory: $treeUri")
                }

                val displayNameRaw = if (name.isBlank()) src.name else name
                val displayName = sanitize(displayNameRaw)
                val (base, ext) = splitBaseExt(displayName)

                val unique = findUniqueDocName(dir, base, ext)
                val mime = getMimeType(unique)

                val dest = dir.createFile(mime, unique)
                    ?: throw DownloadException.StorageError(RuntimeException("Failed to create document in picked folder"))
                createdDocument = dest

                resolver.openOutputStream(dest.uri)?.use { os ->
                    src.inputStream().use { it.copyTo(os) }
                } ?: throw DownloadException.StorageError(
                    RuntimeException("Cannot open output stream for ${dest.uri}")
                )

                dest.uri
            } catch (e: DownloadException) {
                createdDocument?.let { doc ->
                    runCatching { doc.delete() }
                }
                throw e
            } catch (e: Exception) {
                createdDocument?.let { doc ->
                    runCatching { doc.delete() }
                }
                throw DownloadException.StorageError(e)
            }
        }

    private fun getMimeType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a", "aac" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "aiff", "aif" -> "audio/aiff"
            "accurip" -> "text/plain"
            else -> "application/octet-stream"
        }
}




