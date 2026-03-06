package com.psycode.spotiflac.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.frostwire.jlibtorrent.TorrentInfo
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.service.DownloadServiceRouter
import com.psycode.spotiflac.data.service.download.watchdog.DownloadWatchdogScheduler
import com.psycode.spotiflac.data.service.download.orchestration.deleteFileAndPruneParents
import com.psycode.spotiflac.data.service.download.orchestration.normalizeTorrentPath
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.buildDownloadTaskId
import com.psycode.spotiflac.domain.repository.DownloadRepository
import com.psycode.spotiflac.domain.repository.LocalFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val watchdogScheduler: DownloadWatchdogScheduler,
    private val localFileRepository: LocalFileRepository
) : DownloadRepository {
    private val removeMutex = Mutex()

    override suspend fun enqueueDownloads(
        topicId: Int,
        files: List<TorrentFile>,
        saveOption: SaveOption,
        folderUri: String?,
        torrentTitle: String
    ) {
        DownloadLog.d(
            "enqueueDownloads topicId=$topicId files=${files.size} saveOption=$saveOption " +
                "folderSet=${!folderUri.isNullOrBlank()}"
        )
        var hasQueued = false
        files.forEach { file ->
            val now = System.currentTimeMillis()
            val id = buildDownloadTaskId(topicId = topicId, innerPath = file.innerPath)
            val existing = dao.getById(id)
            val existingUri = findExistingMediaStoreAudioUri(
                fileName = file.name,
                expectedSize = file.size
            )
            val resolved = resolveEnqueueEntity(
                id = id,
                file = file,
                existing = existing,
                existingMediaStoreUri = existingUri,
                torrentTitle = torrentTitle,
                saveOption = saveOption,
                folderUri = folderUri,
                now = now
            )
            if (resolved.shouldQueue) hasQueued = true
            dao.upsert(resolved.entity)
            if (resolved.entity.status == DownloadStatus.COMPLETED && resolved.entity.contentUri != null) {
                DownloadLog.d("enqueueDownloads reused existing MediaStore file for taskId=$id")
            }
        }
        if (hasQueued) {
            watchdogScheduler.ensureScheduled()
            DownloadServiceRouter.ensureStarted(context)
        }
    }

    override fun observeDownloads(): Flow<List<DownloadTask>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun pauseDownload(taskId: String) {
        DownloadLog.d("pauseDownload taskId=$taskId")
        DownloadServiceRouter.dispatchTaskAction(
            context = context,
            action = DownloadServiceRouter.ACTION_PAUSE,
            taskId = taskId
        )
    }

    override suspend fun resumeDownload(taskId: String) {
        DownloadLog.d("resumeDownload taskId=$taskId")
        watchdogScheduler.ensureScheduled()
        DownloadServiceRouter.dispatchTaskAction(
            context = context,
            action = DownloadServiceRouter.ACTION_RESUME,
            taskId = taskId
        )
    }

    override suspend fun pauseGroup(topicId: Int) {
        DownloadLog.t(scope = "Repo", message = "pauseGroup topicId=$topicId dispatch=single")
        DownloadServiceRouter.dispatchGroupAction(
            context = context,
            action = DownloadServiceRouter.ACTION_PAUSE_GROUP,
            topicId = topicId
        )
    }

    override suspend fun resumeGroup(topicId: Int) {
        DownloadLog.t(scope = "Repo", message = "resumeGroup topicId=$topicId dispatch=single")
        watchdogScheduler.ensureScheduled()
        DownloadServiceRouter.dispatchGroupAction(
            context = context,
            action = DownloadServiceRouter.ACTION_RESUME_GROUP,
            topicId = topicId
        )
    }

    override suspend fun cancelDownload(taskId: String) {
        DownloadLog.d("cancelDownload taskId=$taskId")
        DownloadServiceRouter.dispatchTaskAction(
            context = context,
            action = DownloadServiceRouter.ACTION_CANCEL,
            taskId = taskId
        )
    }

    override suspend fun removeDownload(taskId: String) {
        removeMutex.withLock {
            DownloadLog.d("removeDownload taskId=$taskId")
            val existing = dao.getById(taskId) ?: return@withLock
            val topicId = parseTopicIdFromTaskId(taskId)
            if (topicId == null) {
                dao.deleteById(taskId)
                deleteDownloadedPayload(listOf(existing))
                return@withLock
            }

            val group = dao.getByTopicPrefix("${topicId}_")
            val hasOtherEntries = group.any { it.id != taskId }
            if (hasOtherEntries) {
                dao.deleteById(taskId)
                deleteDownloadedPayload(listOf(existing))
                return@withLock
            }

            val now = System.currentTimeMillis()
            val anchor = buildGroupAnchorEntity(
                source = existing,
                topicId = topicId,
                now = now
            )
            if (taskId != anchor.id) {
                dao.deleteById(taskId)
            }
            dao.upsert(anchor)
            deleteDownloadedPayload(listOf(existing))
            deleteWholeTorrentPayload(existing.torrentFilePath)
            DownloadLog.t(
                scope = "Repo",
                message = "removeDownload preservedGroupAnchor taskId=$taskId anchorId=${anchor.id} topicId=$topicId"
            )
        }
    }

    





    override suspend fun deleteGroup(topicId: Int, alsoDeleteLocalFiles: Boolean) {
        DownloadLog.d("deleteGroup topicId=$topicId alsoDeleteLocalFiles=$alsoDeleteLocalFiles")
        val prefix = "${topicId}_"
        val activeStatuses = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)

        val group = dao.getByTopicPrefix(prefix)

        group.forEach { e ->
            if (e.status in activeStatuses) {
                dao.upsert(e.copy(status = DownloadStatus.CANCELED))
            }
        }

        repeat(25) {
            val hasActive = dao.getByTopicPrefix(prefix).any { it.status in activeStatuses }
            if (!hasActive) return@repeat
            delay(120)
        }

        dao.deleteByTopicPrefix(prefix)

        if (alsoDeleteLocalFiles) {
            group.asSequence()
                .mapNotNull { it.contentUri }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { uri ->
                    val deleted = runCatching { localFileRepository.deleteByUri(uri) }.getOrDefault(false)
                    DownloadLog.t(
                        scope = "Repo",
                        message = "deleteGroup externalDelete topicId=$topicId uri=$uri deleted=$deleted"
                    )
                }
        }

        
        deleteDownloadedPayload(group)
        group.asSequence()
            .map { it.torrentFilePath }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach(::deleteWholeTorrentPayload)

        
        deleteTorrentCache(topicId)

        
        DownloadServiceRouter.ensureStarted(context)
    }

    

    private fun deleteTorrentCache(topicId: Int) {
        
        runCatching {
            val dir = File(context.filesDir, "torrent_cache")
            val cached = File(dir, "$topicId.torrent")
            if (cached.exists()) cached.delete()
        }

        
        runCatching {
            val oldDir = File(context.cacheDir, "torrents")
            val oldCached = File(oldDir, "t_$topicId.torrent")
            if (oldCached.exists()) oldCached.delete()
        }
        runCatching {
            context.cacheDir
                .listFiles()
                ?.filter { it.name.startsWith("torrent_${topicId}") && it.name.endsWith(".torrent") }
                ?.forEach { it.delete() }
        }
    }

    private fun deleteDownloadedPayload(group: List<DownloadEntity>) {
        val root = context.filesDir
        group.asSequence()
            .map { it.innerPath }
            .distinct()
            .mapNotNull { innerPath -> resolveFileInsideRoot(root, innerPath) }
            .forEach { file ->
                runCatching { deleteFileAndPruneParents(root, file) }
            }
    }

    private fun deleteWholeTorrentPayload(torrentFilePath: String) {
        if (torrentFilePath.isBlank()) return
        val torrentInfo = runCatching { TorrentInfo(File(torrentFilePath)) }.getOrNull() ?: return
        val files = torrentInfo.files()
        val root = context.filesDir
        val numFiles = runCatching { files.numFiles() }.getOrDefault(0)
        for (index in 0 until numFiles) {
            val relativePath = runCatching { files.filePath(index) }.getOrNull().orEmpty()
            val target = resolveFileInsideRoot(root, relativePath) ?: continue
            runCatching { deleteFileAndPruneParents(root, target) }
        }
    }

    private fun findExistingMediaStoreAudioUri(fileName: String, expectedSize: Long): String? {
        if (fileName.isBlank() || expectedSize <= 0L) return null
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND ${MediaStore.Audio.Media.SIZE}=?"
        val selectionArgs = arrayOf(fileName, expectedSize.toString())
        return runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val mediaId = cursor.getLong(idIndex)
                ContentUris.withAppendedId(collection, mediaId).toString()
            }
        }.getOrNull()
    }

    private fun DownloadEntity.toDomain() = DownloadTask(
        id = id,
        fileName = fileName,
        size = size,
        progress = progress,
        status = status,
        errorMessage = errorMessage,
        contentUri = contentUri,
        torrentTitle = torrentTitle,
        speedBytesPerSec = speedBytesPerSec,
        createdAt = createdAt
    )

}

internal data class EnqueueResolution(
    val entity: DownloadEntity,
    val shouldQueue: Boolean
)

internal fun resolveEnqueueEntity(
    id: String,
    file: TorrentFile,
    existing: DownloadEntity?,
    existingMediaStoreUri: String?,
    torrentTitle: String,
    saveOption: SaveOption,
    folderUri: String?,
    now: Long
): EnqueueResolution {
    val saveOptionName = saveOption.name
    val persistedUri = existing?.contentUri ?: existingMediaStoreUri
    val activeStatuses = setOf(DownloadStatus.RUNNING, DownloadStatus.PAUSED, DownloadStatus.QUEUED)
    val shouldKeepActive = existing?.status in activeStatuses
    val shouldMarkCompleted = persistedUri != null

    val resolvedStatus = when {
        shouldKeepActive -> existing!!.status
        shouldMarkCompleted -> DownloadStatus.COMPLETED
        existing?.status in setOf(DownloadStatus.FAILED, DownloadStatus.CANCELED) -> DownloadStatus.QUEUED
        existing?.status == DownloadStatus.COMPLETED -> DownloadStatus.QUEUED
        else -> DownloadStatus.QUEUED
    }

    val progress = when {
        resolvedStatus == DownloadStatus.COMPLETED -> 100
        shouldKeepActive && existing != null -> existing.progress.coerceIn(0, 100)
        else -> 0
    }
    val speed = if (shouldKeepActive && existing != null) existing.speedBytesPerSec else 0L
    val createdAt = if (shouldKeepActive && existing != null) existing.createdAt else now

    val entity = DownloadEntity(
        id = id,
        fileName = file.name,
        size = file.size,
        progress = progress,
        status = resolvedStatus,
        errorMessage = if (resolvedStatus == DownloadStatus.COMPLETED || resolvedStatus == DownloadStatus.QUEUED) {
            null
        } else {
            existing?.errorMessage
        },
        contentUri = if (resolvedStatus == DownloadStatus.COMPLETED) persistedUri else null,
        torrentTitle = torrentTitle,
        torrentFilePath = file.torrentFilePath,
        innerPath = file.innerPath,
        saveOption = saveOptionName,
        folderUri = folderUri ?: existing?.folderUri,
        speedBytesPerSec = speed.coerceAtLeast(0L),
        createdAt = createdAt
    )
    return EnqueueResolution(
        entity = entity,
        shouldQueue = resolvedStatus in activeStatuses
    )
}

internal fun resolveFileInsideRoot(rootDir: File, innerPath: String): File? {
    val safeRelative = normalizeTorrentPath(innerPath)
    if (safeRelative.isBlank()) return null
    val root = rootDir.canonicalFile
    val candidate = File(root, safeRelative).canonicalFile
    val isInside = candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
    return if (isInside) candidate else null
}

private fun parseTopicIdFromTaskId(taskId: String): Int? {
    val separator = taskId.indexOf('_')
    if (separator <= 0) return null
    return taskId.substring(0, separator).toIntOrNull()
}

internal fun buildGroupAnchorEntity(
    source: DownloadEntity,
    topicId: Int,
    now: Long
): DownloadEntity = source.copy(
    id = "${topicId}_group_anchor",
    fileName = "__group_anchor__",
    size = 0L,
    progress = 0,
    status = DownloadStatus.CANCELED,
    errorMessage = null,
    contentUri = null,
    innerPath = "__group_anchor__",
    speedBytesPerSec = 0L,
    createdAt = now
)


