package com.psycode.spotiflac.data.service.download.session

import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import com.psycode.spotiflac.data.service.download.core.DownloadException
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.orchestration.deleteFileAndPruneParents
import com.psycode.spotiflac.data.service.download.orchestration.normalizeTorrentPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TorrentManager(private val session: SessionManager, private val filesDir: File) {
    private val handles = ConcurrentHashMap<String, TorrentHandle>()
    private val torrentInfo = ConcurrentHashMap<String, TorrentInfo>()
    private val activeFiles = ConcurrentHashMap<String, MutableSet<Int>>()
    private val lastAppliedActiveFiles = ConcurrentHashMap<String, Set<Int>>()
    private val pendingCleanupPaths = ConcurrentHashMap<String, MutableSet<String>>()
    private val lock = Mutex()
    private val handleRefCount = ConcurrentHashMap<String, Int>()
    private val lastRecheckAtMs = ConcurrentHashMap<String, Long>()
    private val lastNudgeAtMs = ConcurrentHashMap<String, Long>()
    private val lastRecoverAtMs = ConcurrentHashMap<String, Long>()
    private val lastPeerRefreshAtMs = ConcurrentHashMap<String, Long>()

    suspend fun getOrCreateHandle(ti: TorrentInfo): Pair<String, TorrentHandle> = lock.withLock {
        val key = ti.infoHash().toString()

        val existing = handles[key]
        if (existing != null && existing.isValid) {
            handleRefCount[key] = (handleRefCount[key] ?: 0) + 1
            return key to existing
        }

        try {
            maybeAddFallbackTrackers(ti)
            session.download(ti, filesDir, null, null, null, torrent_flags_t())

            var handle: TorrentHandle? = null
            for (i in 1..10) {
                handle = session.find(ti.infoHash())
                if (handle != null) break
                delay(100)
            }

            handle ?: throw DownloadException.SessionError(
                RuntimeException("No torrent handle found after waiting")
            )

            handles[key] = handle
            torrentInfo[key] = ti
            activeFiles.putIfAbsent(key, mutableSetOf())
            handleRefCount[key] = 1

            return key to handle
        } catch (e: Exception) {
            throw DownloadException.SessionError(e)
        }
    }

    suspend fun releaseHandle(key: String) {
        val cleanupTargets = lock.withLock {
            val count = handleRefCount[key] ?: 0
            if (count > 1) {
                handleRefCount[key] = count - 1
                return@withLock emptyList<File>()
            }

            val handle = handles[key]
            if (handle != null && handle.isValid) {
                runCatching { session.remove(handle) }
            }
            handles.remove(key)
            torrentInfo.remove(key)
            activeFiles.remove(key)
            lastAppliedActiveFiles.remove(key)
            handleRefCount.remove(key)
            lastRecheckAtMs.remove(key)
            lastNudgeAtMs.remove(key)
            lastRecoverAtMs.remove(key)
            lastPeerRefreshAtMs.remove(key)

            drainCleanupTargetsForKey(key)
        }

        cleanupTargets.forEach { target ->
            val deleted = runCatching { deleteFileAndPruneParents(filesDir, target) }.getOrDefault(false)
            DownloadLog.t(
                scope = "Torrent",
                message = "deferredCleanup target='${target.path}' deleted=$deleted key=$key"
            )
        }
    }

    suspend fun deferFileCleanup(
        key: String,
        relativePath: String
    ) = lock.withLock {
        val normalized = normalizeTorrentPath(relativePath)
        if (normalized.isBlank()) return@withLock
        val queue = pendingCleanupPaths.getOrPut(key) { mutableSetOf() }
        queue.add(normalized)
        DownloadLog.t(
            scope = "Torrent",
            message = "deferFileCleanup key=$key path='$normalized' queued=${queue.size}"
        )
    }

    suspend fun applyPriorities(
        key: String,
        force: Boolean = false
    ) = lock.withLock {
        applyPrioritiesLocked(key, force = force)
    }

    private fun applyPrioritiesLocked(
        key: String,
        force: Boolean = false
    ) {
        val h = handles[key] ?: return
        val ti = torrentInfo[key] ?: return
        val set = activeFiles[key]?.toSet() ?: emptySet()
        val previous = lastAppliedActiveFiles[key]
        if (!force && previous == set) return
        val numFiles = ti.files().numFiles()
        val priorities = Priority.array(INACTIVE_FILE_PRIORITY, numFiles)
        set.forEach { index ->
            if (index in 0 until numFiles) {
                priorities[index] = ACTIVE_FILE_PRIORITY
            }
        }
        runCatching {
            h.prioritizeFiles(priorities)
        }.onFailure {
            // Fallback for drivers/devices where bulk priorities call may fail.
            for (i in 0 until numFiles) {
                val p = if (i in set) ACTIVE_FILE_PRIORITY else INACTIVE_FILE_PRIORITY
                runCatching { h.filePriority(i, p) }
            }
        }
        lastAppliedActiveFiles[key] = set
    }

    suspend fun addActiveFile(key: String, fileIndex: Int) = lock.withLock {
        activeFiles[key]?.add(fileIndex)
    }

    suspend fun removeActiveFile(key: String, fileIndex: Int) = lock.withLock {
        activeFiles[key]?.remove(fileIndex)
    }

    fun getHandle(key: String): TorrentHandle? = handles[key]
    fun getTorrentInfo(key: String): TorrentInfo? = torrentInfo[key]

    suspend fun flushCache(key: String) {
        handles[key]?.let { handle ->
            if (handle.isValid) {
                runCatching { handle.flushCache() }
            }
        }
    }

    suspend fun forceRecheckIfStaleCompletion(
        key: String,
        reason: String,
        minIntervalMs: Long = 10_000L
    ): Boolean = lock.withLock {
        val handle = handles[key] ?: return false
        if (!handle.isValid) return false

        val state = runCatching { handle.status(false).state() }.getOrNull()
        if (state == TorrentStatus.State.CHECKING_FILES ||
            state == TorrentStatus.State.CHECKING_RESUME_DATA
        ) {
            return false
        }

        val now = System.currentTimeMillis()
        val last = lastRecheckAtMs[key] ?: 0L
        if (now - last < minIntervalMs) return false

        return runCatching {
            handle.forceRecheck()
            lastRecheckAtMs[key] = now
            DownloadLog.t(scope = "Torrent", message = "forceRecheck key=$key reason=$reason")
            true
        }.getOrElse {
            DownloadLog.e("forceRecheck failed key=$key reason=$reason", it)
            false
        }
    }

    suspend fun nudgeHandle(
        key: String,
        reason: String,
        minIntervalMs: Long = 5_000L
    ): Boolean = lock.withLock {
        val handle = handles[key] ?: return false
        if (!handle.isValid) return false

        val now = System.currentTimeMillis()
        val last = lastNudgeAtMs[key] ?: 0L
        if (now - last < minIntervalMs) return false

        val state = runCatching { handle.status(false).state() }.getOrNull()
        if (state == TorrentStatus.State.CHECKING_FILES ||
            state == TorrentStatus.State.CHECKING_RESUME_DATA
        ) {
            return false
        }

        return runCatching {
            handle.pause()
            handle.resume()
            lastNudgeAtMs[key] = now
            DownloadLog.t(scope = "Torrent", message = "nudgeHandle key=$key reason=$reason")
            true
        }.getOrElse {
            DownloadLog.e("nudgeHandle failed key=$key reason=$reason", it)
            false
        }
    }

    suspend fun recycleHandle(
        key: String,
        reason: String
    ): Boolean = lock.withLock {
        val handle = handles[key] ?: return false
        return runCatching {
            if (handle.isValid) {
                handle.pause()
                session.remove(handle)
            }
            handles.remove(key)
            torrentInfo.remove(key)
            activeFiles.remove(key)
            lastAppliedActiveFiles.remove(key)
            handleRefCount.remove(key)
            val cleanupTargets = drainCleanupTargetsForKey(key)
            lastRecheckAtMs.remove(key)
            lastNudgeAtMs.remove(key)
            DownloadLog.t(scope = "Torrent", message = "recycleHandle key=$key reason=$reason")
            cleanupTargets.forEach { target ->
                runCatching { deleteFileAndPruneParents(filesDir, target) }
            }
            true
        }.getOrElse {
            DownloadLog.e("recycleHandle failed key=$key reason=$reason", it)
            false
        }
    }

    suspend fun refreshPeerDiscovery(
        key: String,
        reason: String,
        allowDhtAnnounce: Boolean,
        minIntervalMs: Long = 30_000L
    ): Boolean = lock.withLock {
        val handle = handles[key] ?: return false
        if (!handle.isValid) return false
        val now = System.currentTimeMillis()
        val last = lastPeerRefreshAtMs[key] ?: 0L
        if (now - last < minIntervalMs) return false

        return runCatching {
            handle.forceReannounce()
            if (allowDhtAnnounce) {
                handle.forceDHTAnnounce()
            }
            lastPeerRefreshAtMs[key] = now
            DownloadLog.t(
                scope = "Torrent",
                message =
                "refreshPeerDiscovery key=$key reason=$reason dht=$allowDhtAnnounce"
            )
            true
        }.getOrElse {
            DownloadLog.e("refreshPeerDiscovery failed key=$key reason=$reason", it)
            false
        }
    }

    suspend fun recoverHandle(
        key: String,
        reason: String,
        minIntervalMs: Long = 15_000L
    ): Boolean = lock.withLock {
        val ti = torrentInfo[key] ?: return false
        val active = (activeFiles[key] ?: emptySet()).toMutableSet()
        val refs = (handleRefCount[key] ?: 1).coerceAtLeast(1)
        if (refs > 1 || active.size > 1) {
            DownloadLog.t(
                scope = "Torrent",
                message = "recoverHandle skipped key=$key reason=$reason refs=$refs activeFiles=${active.size}"
            )
            return false
        }
        val old = handles[key]
        val now = System.currentTimeMillis()
        val last = lastRecoverAtMs[key] ?: 0L
        if (now - last < minIntervalMs) {
            DownloadLog.t(
                scope = "Torrent",
                message = "recoverHandle skipped key=$key reason=$reason cooldown=${now - last}ms"
            )
            return false
        }

        return try {
            if (old != null && old.isValid) {
                old.pause()
                session.remove(old)
            }

            session.download(ti, filesDir, null, null, null, torrent_flags_t())

            var recovered: TorrentHandle? = null
            for (i in 1..20) {
                recovered = session.find(ti.infoHash())
                if (recovered != null && recovered.isValid) break
                delay(100)
            }
            val handle = recovered ?: throw RuntimeException("Failed to recover handle for key=$key")

            handles[key] = handle
            activeFiles[key] = active
            handleRefCount[key] = refs
            lastAppliedActiveFiles.remove(key)
            lastRecheckAtMs.remove(key)
            lastNudgeAtMs.remove(key)
            lastRecoverAtMs[key] = now
            lastPeerRefreshAtMs.remove(key)
            applyPrioritiesLocked(key, force = true)

            DownloadLog.t(
                scope = "Torrent",
                message = "recoverHandle key=$key reason=$reason refs=$refs activeFiles=${active.size}"
            )
            true
        } catch (t: Throwable) {
            DownloadLog.e("recoverHandle failed key=$key reason=$reason", t)
            false
        }
    }

    fun cleanup() {
        val cleanupTargets = pendingCleanupPaths.values
            .asSequence()
            .flatMap { it.asSequence() }
            .mapNotNull(::resolveCleanupTarget)
            .distinctBy { it.path }
            .toList()

        handles.values.forEach { handle ->
            runCatching {
                if (handle.isValid) {
                    handle.pause()
                    session.remove(handle)
                }
            }
        }
        handles.clear()
        torrentInfo.clear()
        activeFiles.clear()
        lastAppliedActiveFiles.clear()
        handleRefCount.clear()
        lastRecheckAtMs.clear()
        lastNudgeAtMs.clear()
        lastRecoverAtMs.clear()
        lastPeerRefreshAtMs.clear()
        pendingCleanupPaths.clear()

        cleanupTargets.forEach { target ->
            runCatching { deleteFileAndPruneParents(filesDir, target) }
        }
    }

    private fun maybeAddFallbackTrackers(ti: TorrentInfo) {
        if (ti.isPrivate) return
        val existing = ti.trackers()
            .mapNotNull { tracker -> runCatching { tracker.url() }.getOrNull() }
            .toSet()
        FALLBACK_PUBLIC_TRACKERS
            .asSequence()
            .filter { url -> url !in existing }
            .forEach { url ->
                runCatching { ti.addTracker(url) }
            }
    }

    private fun resolveCleanupTarget(relativePath: String): File? {
        val normalized = normalizeTorrentPath(relativePath)
        if (normalized.isBlank()) return null
        val root = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(root, normalized).canonicalFile }.getOrNull() ?: return null
        val insideRoot = candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
        return if (insideRoot) candidate else null
    }

    private fun drainCleanupTargetsForKey(key: String): List<File> =
        pendingCleanupPaths.remove(key)
            .orEmpty()
            .mapNotNull(::resolveCleanupTarget)
}

private val FALLBACK_PUBLIC_TRACKERS = listOf(
    "udp://open.stealth.si:80/announce",
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce"
)

private val ACTIVE_FILE_PRIORITY = Priority.FOUR
private val INACTIVE_FILE_PRIORITY = Priority.NORMAL




