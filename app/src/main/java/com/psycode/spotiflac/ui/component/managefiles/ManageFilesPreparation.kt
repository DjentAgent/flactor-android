package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.buildDownloadTaskId
import kotlin.math.max

data class UiFileDl(
    val file: TorrentFile,
    val taskId: String?,
    val normalizedName: String,
    val normalizedPath: String,
    val dupKey: String,
    val size: Long,
    val status: DownloadStatus?,
    val progress: Int?,
    val speedBytesPerSec: Long = 0L,
    val queuePosition: Int? = null,
    val errorMessage: String? = null,
    val contentUri: String?,
    val completedAt: Long?,
    val disabled: Boolean,
    val matchScore: Int
)

data class PreparedFilesDl(
    val ordered: List<UiFileDl>,
    val indexByPath: Map<String, Int>,
    val selectableIndices: List<Int>,
    val removableCompletedIndices: List<Int>,
    val initialChecks: List<Boolean>,
    val duplicateKeys: Set<String>,
    val matchedIndices: Set<Int>
)

fun prepareFilesDl(
    files: List<TorrentFile>,
    tasks: List<DownloadTask>,
    topicId: Int?,
    groupTitle: String,
    groupNorm: String,
    autoMatchEnabled: Boolean,
    autoMatchArtist: String?,
    autoMatchTitle: String?,
    autoMatchMinFuzzy: Double
): PreparedFilesDl {
    val nonAudioExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "pdf", "txt", "nfo", "sfv", "m3u", "m3u8", "cue", "log", "url", "html"
    )
    val existingById: Map<String, DownloadTask> = tasks.associateBy { it.id }
    val topicTasks = topicId?.let { targetTopic ->
        tasks.filter { task -> parseTopicIdPrefix(task.id) == targetTopic }
    }.orEmpty()
    val queuedPositionsByTaskId = topicTasks
        .asSequence()
        .filter { task -> task.status == DownloadStatus.QUEUED }
        .sortedByDescending { task -> task.createdAt }
        .map { task -> task.id }
        .withIndex()
        .associate { indexed -> indexed.value to (indexed.index + 1) }

    val uiList = files.asSequence()
        .filter { file -> fileExtension(file.name) !in nonAudioExtensions }
        .map { file ->
            val predictedTaskId = topicId?.let { buildDownloadTaskId(it, file.innerPath) }
            val task = predictedTaskId?.let(existingById::get)
                ?: matchTaskByNameAndSize(topicTasks = topicTasks, file = file)
            val resolvedTaskId = task?.id ?: predictedTaskId
            val status = task?.status
            val disabled = status in setOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED
            )
            val baseName = fileBaseName(file.name)
            UiFileDl(
                file = file,
                taskId = resolvedTaskId,
                normalizedName = normalizeCore(baseName),
                normalizedPath = normalizeCore(file.innerPath.replace('\\', '/')),
                dupKey = duplicateKeyOf(baseName),
                size = file.size,
                status = status,
                progress = task?.progress,
                speedBytesPerSec = task?.speedBytesPerSec ?: 0L,
                queuePosition = task?.id?.let(queuedPositionsByTaskId::get),
                errorMessage = task?.errorMessage,
                contentUri = task?.contentUri,
                completedAt = if (status == DownloadStatus.COMPLETED) task?.createdAt else null,
                disabled = disabled,
                matchScore = calculateListTitleScore(file.name, file.size, groupTitle)
            )
        }
        .toList()

    val ordered = uiList.sortedWith(
        compareByDescending<UiFileDl> { it.status == DownloadStatus.COMPLETED }
            .thenByDescending { it.completedAt ?: Long.MIN_VALUE }
            .thenByDescending { it.matchScore }
            .thenByDescending { it.size }
    )

    val duplicateKeys: Set<String> = ordered.groupBy { it.dupKey }
        .filterValues { it.size > 1 }.keys

    val indexByPath = ordered.withIndex().associate { it.value.file.innerPath to it.index }
    val selectableIndices = ordered.indices.filter { index ->
        ordered[index].status !in setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
            DownloadStatus.COMPLETED
        )
    }
    val removableCompletedIndices = ordered.indices.filter { index ->
        ordered[index].status == DownloadStatus.COMPLETED
    }

    val initialChecksRaw = ordered.map { ui ->
        ui.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)
    }

    val matchedIndices: Set<Int> = if (autoMatchEnabled) {
        val artistNorm = normalizeCore(autoMatchArtist.orEmpty())
        val rawTitleNorm = normalizeCore(autoMatchTitle.orEmpty())
        val titleNorm = if (rawTitleNorm.isNotEmpty()) rawTitleNorm else groupNorm
        val titleCompact = titleNorm.replace(" ", "")

        data class Candidate(
            val idx: Int,
            val kind: String,
            val fuzzy: Double,
            val baseScore: Int,
            val size: Long
        )

        val candidates = mutableListOf<Candidate>()

        ordered.forEachIndexed { index, ui ->
            val normalized = ui.normalizedName
            val titleParts = buildTitleCandidates(normalized, artistNorm)

            var bestFuzzy = 0.0
            var bestKind = "none"
            var score = 0

            if (titleNorm.isNotEmpty()) {
                val hasExact = titleParts.any { it == titleNorm || it == titleCompact }
                val hasContains = !hasExact && titleParts.any { it.contains(titleNorm) || it.contains(titleCompact) }

                when {
                    hasExact -> {
                        bestKind = "exact"
                        bestFuzzy = 1.0
                        score += 90
                    }
                    hasContains -> {
                        bestKind = "contains"
                        bestFuzzy = 0.95
                        score += 72
                    }
                    else -> {
                        bestFuzzy = titleParts.maxOfOrNull { part ->
                            val s1 = damerauLevenshteinSimilarity(part, titleNorm)
                            val s2 = damerauLevenshteinSimilarity(part, titleCompact)
                            max(s1, s2)
                        } ?: 0.0
                        if (bestFuzzy >= autoMatchMinFuzzy) {
                            bestKind = "fuzzy"
                            score += 60
                        }
                    }
                }
            }

            if (artistNorm.isNotEmpty() && (normalized.contains(artistNorm) || ui.normalizedPath.contains(artistNorm))) {
                score += 18
            }

            if (rawTitleNorm.isEmpty() && artistNorm.isNotEmpty()) {
                if (bestKind == "none" && (normalized.contains(artistNorm) || ui.normalizedPath.contains(artistNorm))) {
                    bestKind = "artist_only"
                }
                score += when {
                    ui.size >= 150L * 1024 * 1024 -> 14
                    ui.size >= 80L * 1024 * 1024 -> 10
                    else -> 6
                }
            }

            score += when {
                ui.size >= 150L * 1024 * 1024 -> 6
                ui.size >= 80L * 1024 * 1024 -> 3
                else -> 0
            }

            val shouldAdd = when {
                bestKind == "exact" || bestKind == "contains" -> true
                bestKind == "fuzzy" && bestFuzzy >= autoMatchMinFuzzy -> true
                bestKind == "artist_only" && score >= 20 -> true
                else -> false
            }

            if (shouldAdd) {
                candidates += Candidate(
                    idx = index,
                    kind = bestKind,
                    fuzzy = bestFuzzy,
                    baseScore = score,
                    size = ui.size
                )
            }
        }

        fun kindRank(kind: String): Int = when (kind) {
            "exact" -> 5
            "contains" -> 4
            "fuzzy" -> 3
            "artist_only" -> 2
            else -> 1
        }

        val best = candidates.maxWithOrNull(
            compareByDescending<Candidate> { kindRank(it.kind) }
                .thenByDescending { it.fuzzy }
                .thenByDescending { it.baseScore }
                .thenByDescending { it.size }
        )

        val accepted = when {
            best == null -> false
            best.kind == "exact" -> true
            best.kind == "contains" -> best.baseScore >= 50
            best.kind == "fuzzy" -> (best.fuzzy >= autoMatchMinFuzzy && best.baseScore >= 50)
            best.kind == "artist_only" -> (rawTitleNorm.isEmpty() && best.baseScore >= 28)
            else -> false
        }

        if (accepted) setOf(best!!.idx) else emptySet()
    } else {
        emptySet()
    }

    val finalInitialChecks = initialChecksRaw.toMutableList().also { checks ->
        matchedIndices.forEach { matchedIndex ->
            if (matchedIndex in selectableIndices && matchedIndex in checks.indices) checks[matchedIndex] = true
        }
    }

    return PreparedFilesDl(
        ordered = ordered,
        indexByPath = indexByPath,
        selectableIndices = selectableIndices,
        removableCompletedIndices = removableCompletedIndices,
        initialChecks = finalInitialChecks,
        duplicateKeys = duplicateKeys,
        matchedIndices = matchedIndices
    )
}

private fun parseTopicIdPrefix(taskId: String): Int? {
    val separatorIndex = taskId.indexOf('_')
    if (separatorIndex <= 0) return null
    return taskId.substring(0, separatorIndex).toIntOrNull()
}

private fun matchTaskByNameAndSize(
    topicTasks: List<DownloadTask>,
    file: TorrentFile
): DownloadTask? {
    if (topicTasks.isEmpty()) return null
    val normalizedFileName = normalizeCore(file.name)
    val candidates = topicTasks.filter { task ->
        val sameName = normalizeCore(task.fileName) == normalizedFileName
        if (!sameName) return@filter false
        if (file.size > 0L && task.size > 0L) {
            task.size == file.size
        } else {
            true
        }
    }
    return candidates.singleOrNull()
}
