package com.psycode.spotiflac.ui.component.managefiles

import androidx.compose.ui.state.ToggleableState
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageFilesExplorerTreeTest {

    @Test
    fun `build tree sorts children and computes descendants deterministically`() {
        val prep = prepOf(
            ordered = listOf(
                ui("zeta/02.flac", DownloadStatus.QUEUED),
                ui("alpha/01.flac", DownloadStatus.QUEUED),
                ui("alpha/sub/03.flac", DownloadStatus.QUEUED)
            ),
            selectableIndices = listOf(0, 1, 2),
            matchedIndices = emptySet()
        )

        val tree = buildManageFilesExplorerTree(prep)
        assertEquals(listOf("alpha", "zeta"), tree.nodes.getValue("").children)
        assertEquals(listOf(1, 2), tree.descendantIndices.getValue("alpha"))
    }

    @Test
    fun `initial expanded dirs include ancestors of matched and completed`() {
        val prep = prepOf(
            ordered = listOf(
                ui("done/file.flac", DownloadStatus.COMPLETED),
                ui("a/b/c.flac", DownloadStatus.QUEUED)
            ),
            selectableIndices = listOf(1),
            matchedIndices = setOf(1)
        )

        val tree = buildManageFilesExplorerTree(prep)
        assertTrue(tree.initialExpandedDirs.contains(""))
        assertTrue(tree.initialExpandedDirs.contains("done"))
        assertTrue(tree.initialExpandedDirs.contains("a"))
        assertTrue(tree.initialExpandedDirs.contains("a/b"))
    }

    @Test
    fun `visible rows with query include only matching branch`() {
        val prep = prepOf(
            ordered = listOf(
                ui("alpha/sub/target.flac", DownloadStatus.QUEUED),
                ui("beta/other.flac", DownloadStatus.QUEUED)
            ),
            selectableIndices = listOf(0, 1),
            matchedIndices = emptySet()
        )
        val tree = buildManageFilesExplorerTree(prep)
        val rows = buildVisibleExplorerRows(
            tree = tree,
            prep = prep,
            expandedDirs = emptySet(),
            queryNorm = normalizeCore("target"),
            fileMatchesPredicate = { ui ->
                ui.normalizedName.contains(normalizeCore("target")) ||
                    ui.normalizedPath.contains(normalizeCore("target"))
            }
        )

        val dirKeys = rows.filterIsInstance<ManageFilesExplorerRow.DirRow>().map { it.node.key }
        assertTrue(dirKeys.contains(""))
        assertTrue(dirKeys.contains("alpha"))
        assertTrue(dirKeys.contains("alpha/sub"))
        assertTrue(!dirKeys.contains("beta"))
    }

    @Test
    fun `folder toggle and apply selection affect only selectable indices`() {
        val prep = prepOf(
            ordered = listOf(
                ui("a/1.flac", DownloadStatus.QUEUED),
                ui("a/2.flac", DownloadStatus.COMPLETED)
            ),
            selectableIndices = listOf(0),
            matchedIndices = emptySet()
        )
        val tree = buildManageFilesExplorerTree(prep)
        val checks = listOf(false, true)

        val (stateBefore, enabledBefore) = folderToggleStateForKey(tree, "a", prep.selectableIndices, checks)
        assertEquals(ToggleableState.Off, stateBefore)
        assertTrue(enabledBefore)

        val updated = applyFolderSelectionForKey(tree, "a", prep.selectableIndices, checks, toChecked = true)
        assertEquals(listOf(true, true), updated)

        val (stateAfter, enabledAfter) = folderToggleStateForKey(tree, "a", prep.selectableIndices, updated)
        assertEquals(ToggleableState.On, stateAfter)
        assertTrue(enabledAfter)
    }

    @Test
    fun `build tree normalizes mixed separators and dot segments`() {
        val prep = prepOf(
            ordered = listOf(
                ui("a//b\\c/../d/song.flac", DownloadStatus.QUEUED),
                ui("./a/b/./e/track.flac", DownloadStatus.QUEUED)
            ),
            selectableIndices = listOf(0, 1),
            matchedIndices = emptySet()
        )

        val tree = buildManageFilesExplorerTree(prep)
        assertTrue(tree.nodes.containsKey("a"))
        assertTrue(tree.nodes.containsKey("a/b"))
        assertTrue(tree.nodes.containsKey("a/b/d"))
        assertTrue(tree.nodes.containsKey("a/b/e"))
        assertTrue(!tree.nodes.containsKey("a/b/c"))
        assertEquals(listOf("a/b/d", "a/b/e"), tree.nodes.getValue("a/b").children)
    }

    @Test
    fun `folder helpers ignore stale indices safely`() {
        val prep = prepOf(
            ordered = listOf(ui("a/1.flac", DownloadStatus.QUEUED)),
            selectableIndices = listOf(0),
            matchedIndices = emptySet()
        )
        val tree = ManageFilesExplorerTree(
            nodes = mapOf("" to ManageFilesDirNode("", "/", "", "", 0, emptyList(), emptyList())),
            descendantIndices = mapOf("" to listOf(0, 99, 99)),
            initialExpandedDirs = setOf("")
        )
        val checks = listOf(true)

        val (state, enabled) = folderToggleStateForKey(tree, "", prep.selectableIndices, checks)
        assertEquals(ToggleableState.On, state)
        assertTrue(enabled)

        val updated = applyFolderSelectionForKey(tree, "", prep.selectableIndices, checks, toChecked = false)
        assertEquals(listOf(false), updated)
    }

    @Test
    fun `visible rows skip invalid direct file indices`() {
        val prep = prepOf(
            ordered = listOf(ui("a/ok.flac", DownloadStatus.QUEUED)),
            selectableIndices = listOf(0),
            matchedIndices = emptySet()
        )
        val tree = ManageFilesExplorerTree(
            nodes = mapOf(
                "" to ManageFilesDirNode("", "/", "", "", 0, listOf("a"), emptyList()),
                "a" to ManageFilesDirNode("a", "a", "a", "a", 1, emptyList(), listOf(0, 42))
            ),
            descendantIndices = mapOf("" to listOf(0), "a" to listOf(0)),
            initialExpandedDirs = setOf("", "a")
        )

        val rows = buildVisibleExplorerRows(
            tree = tree,
            prep = prep,
            expandedDirs = setOf("", "a"),
            queryNorm = "",
            fileMatchesPredicate = { true }
        )
        val fileRows = rows.filterIsInstance<ManageFilesExplorerRow.FileRow>()
        assertEquals(1, fileRows.size)
        assertEquals(0, fileRows.first().index)
    }

    private fun prepOf(
        ordered: List<UiFileDl>,
        selectableIndices: List<Int>,
        matchedIndices: Set<Int>
    ): PreparedFilesDl {
        val indexByPath = ordered.withIndex().associate { it.value.file.innerPath to it.index }
        return PreparedFilesDl(
            ordered = ordered,
            indexByPath = indexByPath,
            selectableIndices = selectableIndices,
            removableCompletedIndices = ordered.indices.filter { ordered[it].status == DownloadStatus.COMPLETED },
            initialChecks = ordered.indices.map { it in selectableIndices },
            duplicateKeys = emptySet(),
            matchedIndices = matchedIndices
        )
    }

    private fun ui(path: String, status: DownloadStatus): UiFileDl {
        val file = TorrentFile(
            name = path.substringAfterLast('/'),
            size = 10L,
            torrentFilePath = "/tmp/a.torrent",
            innerPath = path
        )
        return UiFileDl(
            file = file,
            taskId = "task-${normalizeCore(path)}",
            normalizedName = normalizeCore(fileBaseName(file.name)),
            normalizedPath = normalizeCore(path),
            dupKey = duplicateKeyOf(fileBaseName(file.name)),
            size = file.size,
            status = status,
            progress = if (status == DownloadStatus.RUNNING) 40 else null,
            contentUri = if (status == DownloadStatus.COMPLETED) "content://$path" else null,
            completedAt = if (status == DownloadStatus.COMPLETED) 1L else null,
            disabled = status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED),
            matchScore = 0
        )
    }
}
