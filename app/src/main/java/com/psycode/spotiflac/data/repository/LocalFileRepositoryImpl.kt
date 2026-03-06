package com.psycode.spotiflac.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.psycode.spotiflac.domain.repository.LocalFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalFileRepository {

    override suspend fun deleteByUri(uri: String): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        return try {
            when (parsed.scheme?.lowercase()) {
                "content" -> {
                    val rows = context.contentResolver.delete(parsed, null, null)
                    if (rows > 0) {
                        true
                    } else {
                        val doc = DocumentFile.fromSingleUri(context, parsed)
                        doc?.delete() == true
                    }
                }
                "file" -> {
                    val file = File(parsed.path ?: return false)
                    file.exists() && file.delete()
                }
                else -> false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
