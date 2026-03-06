package com.psycode.spotiflac.ui.component.managefiles

import androidx.compose.ui.state.ToggleableState
import com.psycode.spotiflac.domain.model.DownloadStatus

data class ManageFilesDirNode(
    val key: String,
    val name: String,
    val nameNorm: String,
    val keyNorm: String,
    val depth: Int,
    val children: List<String>,
    val directFileIndices: List<Int>
)

sealed class ManageFilesExplorerRow {
    data class DirRow(val node: ManageFilesDirNode) : ManageFilesExplorerRow()
    data class FileRow(val ui: UiFileDl, val index: Int, val depth: Int) : ManageFilesExplorerRow()
}

data class ManageFilesExplorerTree(
    val nodes: Map<String, ManageFilesDirNode>,
    val descendantIndices: Map<String, List<Int>>,
    val initialExpandedDirs: Set<String>
)

private fun normalizedPathSegments(path: String): List<String> {
    if (path.isBlank()) return emptyList()
    val result = mutableListOf<String>()
    path.replace('\\', '/')
        .split('/')
        .forEach { raw ->
            val segment = raw.trim()
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                else -> result += segment
            }
        }
    return result
}

fun buildManageFilesExplorerTree(prep: PreparedFilesDl): ManageFilesExplorerTree {
    data class MutableNode(
        val key: String,
        var name: String,
        var nameNorm: String,
        var keyNorm: String,
        var depth: Int,
        val children: MutableSet<String>,
        val directFileIndices: MutableList<Int>
    )

    val mutableNodes = mutableMapOf<String, MutableNode>()

    fun ensureNode(key: String): MutableNode = mutableNodes.getOrPut(key) {
        val depth = if (key.isEmpty()) 0 else key.count { it == '/' } + 1
        val name = if (key.isEmpty()) "/" else key.substringAfterLast('/')
        MutableNode(
            key = key,
            name = name,
            nameNorm = normalizeCore(name),
            keyNorm = normalizeCore(key),
            depth = depth,
            children = mutableSetOf(),
            directFileIndices = mutableListOf()
        )
    }

    prep.ordered.forEachIndexed { index, ui ->
        val segments = normalizedPathSegments(ui.file.innerPath)
        val dirSegments = if (segments.size > 1) segments.dropLast(1) else emptyList()
        val dirPath = dirSegments.joinToString("/")
        var accumulated = ""
        var parentKey = ""
        dirSegments.forEach { segment ->
            accumulated = if (accumulated.isEmpty()) segment else "$accumulated/$segment"
            ensureNode(accumulated)
            ensureNode(parentKey).children.add(accumulated)
            parentKey = accumulated
        }

        val node = ensureNode(dirPath)
        node.directFileIndices.add(index)
    }
    ensureNode("")

    val nodes = mutableNodes.mapValues { (_, node) ->
        ManageFilesDirNode(
            key = node.key,
            name = node.name,
            nameNorm = node.nameNorm,
            keyNorm = node.keyNorm,
            depth = node.depth,
            children = node.children.sorted(),
            directFileIndices = node.directFileIndices.toList()
        )
    }

    val descendantCache = mutableMapOf<String, List<Int>>()
    fun descendants(key: String, path: MutableSet<String> = mutableSetOf()): List<Int> {
        descendantCache[key]?.let { return it }
        if (!path.add(key)) return emptyList()

        val node = nodes[key]
        if (node == null) return emptyList()

        val values = mutableListOf<Int>()
        values.addAll(node.directFileIndices)
        node.children.forEach { child ->
            values.addAll(descendants(child, path))
        }
        path.remove(key)

        val distinct = values.distinct()
        descendantCache[key] = distinct
        return distinct
    }
    nodes.keys.forEach { descendants(it) }

    val initialExpanded = mutableSetOf("")
    fun addAncestors(path: String) {
        var key = path.replace('\\', '/').trim('/').substringBeforeLast('/', "")
        while (true) {
            initialExpanded.add(key)
            if (key.isEmpty()) break
            key = key.substringBeforeLast('/', "")
        }
    }
    prep.matchedIndices.forEach { idx -> addAncestors(prep.ordered[idx].file.innerPath) }
    prep.ordered.forEach { ui ->
        if (ui.status == DownloadStatus.COMPLETED) addAncestors(ui.file.innerPath)
    }

    return ManageFilesExplorerTree(
        nodes = nodes,
        descendantIndices = descendantCache,
        initialExpandedDirs = initialExpanded
    )
}

fun folderToggleStateForKey(
    tree: ManageFilesExplorerTree,
    key: String,
    selectableIndices: Collection<Int>,
    checks: List<Boolean>
): Pair<ToggleableState, Boolean> {
    val all = tree.descendantIndices[key].orEmpty().distinct()
    if (all.isEmpty()) return ToggleableState.Off to false

    val selectableSet = selectableIndices.toSet()
    val selectable = all.filter { it in checks.indices && it in selectableSet }
    if (selectable.isEmpty()) return ToggleableState.Off to false

    val selectedCount = selectable.count { checks.getOrNull(it) == true }
    val state = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == selectable.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    return state to true
}

fun applyFolderSelectionForKey(
    tree: ManageFilesExplorerTree,
    key: String,
    selectableIndices: Collection<Int>,
    checks: List<Boolean>,
    toChecked: Boolean
): List<Boolean> {
    val all = tree.descendantIndices[key].orEmpty().distinct()
    val selectableSet = selectableIndices.toSet()
    val selectable = all.filter { it in checks.indices && it in selectableSet }
    if (selectable.isEmpty()) return checks
    val result = checks.toMutableList()
    selectable.forEach { i -> if (i < result.size) result[i] = toChecked }
    return result
}

fun buildVisibleExplorerRows(
    tree: ManageFilesExplorerTree,
    prep: PreparedFilesDl,
    expandedDirs: Set<String>,
    queryNorm: String,
    fileMatchesPredicate: (UiFileDl) -> Boolean
): List<ManageFilesExplorerRow> {
    fun dirMatches(key: String): Boolean {
        if (queryNorm.isBlank()) return true
        val node = tree.nodes[key] ?: return false
        return node.nameNorm.contains(queryNorm) || node.keyNorm.contains(queryNorm)
    }

    val deepMatchCache = mutableMapOf<String, Boolean>()
    fun dirHasMatchDeep(key: String): Boolean {
        deepMatchCache[key]?.let { return it }
        val node = tree.nodes[key] ?: return false
        if (queryNorm.isNotBlank() && dirMatches(key)) return true.also { deepMatchCache[key] = true }
        if (node.directFileIndices.any { idx ->
                prep.ordered.getOrNull(idx)?.let(fileMatchesPredicate) == true
            }) {
            deepMatchCache[key] = true
            return true
        }
        val hasInChildren = node.children.any { child -> dirHasMatchDeep(child) }
        deepMatchCache[key] = hasInChildren
        return hasInChildren
    }

    fun dirHasMatch(key: String): Boolean =
        tree.descendantIndices[key].orEmpty().any { it in prep.matchedIndices }

    fun dirHasDownloaded(key: String): Boolean =
        tree.descendantIndices[key].orEmpty().any { idx ->
            prep.ordered.getOrNull(idx)?.status == DownloadStatus.COMPLETED
        }

    val rows = mutableListOf<ManageFilesExplorerRow>()
    fun traverse(key: String, forceShow: Boolean) {
        val node = tree.nodes[key] ?: return
        val shouldShowNode = forceShow || dirHasMatchDeep(key)
        if (!shouldShowNode) return
        rows.add(ManageFilesExplorerRow.DirRow(node))

        val expanded = if (queryNorm.isBlank()) expandedDirs.contains(key) else true
        if (!expanded) return

        val children = node.children
            .filter { child -> dirHasMatchDeep(child) }
            .sortedWith(
                compareByDescending<String> { child -> dirHasDownloaded(child) }
                    .thenByDescending { child -> dirHasMatch(child) }
                    .thenBy { it }
            )
        children.forEach { child -> traverse(child, forceShow = false) }

        val filesHere = node.directFileIndices
            .filter { idx ->
                val ui = prep.ordered.getOrNull(idx) ?: return@filter false
                fileMatchesPredicate(ui)
            }
            .sortedWith(
                compareByDescending<Int> { idx -> prep.ordered[idx].status == DownloadStatus.COMPLETED }
                    .thenByDescending { idx -> idx in prep.matchedIndices }
                    .thenByDescending { idx -> prep.ordered[idx].size }
                    .thenBy { idx -> prep.ordered[idx].normalizedPath }
            )

        filesHere.forEach { idx ->
            rows.add(ManageFilesExplorerRow.FileRow(prep.ordered[idx], idx, node.depth + 1))
        }
    }

    traverse("", forceShow = false)
    return rows
}
