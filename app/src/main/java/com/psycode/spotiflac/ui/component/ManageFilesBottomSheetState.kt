package com.psycode.spotiflac.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesFilter
import com.psycode.spotiflac.ui.component.managefiles.PreparedFilesDl
import com.psycode.spotiflac.ui.component.managefiles.RemoveCandidate

internal data class ScrollAnchor(
    val index: Int,
    val offset: Int
)

enum class ManageFilesSortOption {
    RELEVANCE,
    SIZE,
    NAME
}

@Stable
class ManageFilesState internal constructor(
    val listModeState: LazyListState,
    val explorerModeState: LazyListState,
    private val fileQueryState: MutableState<String>,
    private val checksState: MutableState<List<Boolean>>,
    private val orderedForSaveState: MutableState<List<TorrentFile>>,
    private val selectableForSaveState: MutableState<List<Int>>,
    private val showSaveDialogState: MutableState<Boolean>,
    private val removeCandidateState: MutableState<RemoveCandidate?>,
    private val bulkRemoveCandidatesState: MutableState<List<RemoveCandidate>>,
    private val alsoDeleteState: MutableState<Boolean>,
    private val viewModeState: MutableState<FilesViewMode>,
    private val expandedDirsState: MutableState<Set<String>>,
    private val fileFilterState: MutableState<ManageFilesFilter>,
    private val sortOptionState: MutableState<ManageFilesSortOption>,
    private val listScrollAnchorsState: MutableState<Map<ManageFilesFilter, ScrollAnchor>>,
    private val explorerScrollAnchorsState: MutableState<Map<ManageFilesFilter, ScrollAnchor>>,
    private val contentKeyState: MutableState<String?>
) {
    var fileQuery: String
        get() = fileQueryState.value
        set(value) {
            fileQueryState.value = value
        }

    var checks: List<Boolean>
        get() = checksState.value
        set(value) {
            checksState.value = value
        }

    var orderedForSave: List<TorrentFile>
        get() = orderedForSaveState.value
        set(value) {
            orderedForSaveState.value = value
        }

    var selectableForSave: List<Int>
        get() = selectableForSaveState.value
        set(value) {
            selectableForSaveState.value = value
        }

    var showSaveDialog: Boolean
        get() = showSaveDialogState.value
        set(value) {
            showSaveDialogState.value = value
        }

    var removeCandidate: RemoveCandidate?
        get() = removeCandidateState.value
        set(value) {
            removeCandidateState.value = value
        }

    var bulkRemoveCandidates: List<RemoveCandidate>
        get() = bulkRemoveCandidatesState.value
        set(value) {
            bulkRemoveCandidatesState.value = value
        }

    var alsoDelete: Boolean
        get() = alsoDeleteState.value
        set(value) {
            alsoDeleteState.value = value
        }

    var viewMode: FilesViewMode
        get() = viewModeState.value
        set(value) {
            viewModeState.value = value
        }

    var expandedDirs: Set<String>
        get() = expandedDirsState.value
        set(value) {
            expandedDirsState.value = value
        }

    var fileFilter: ManageFilesFilter
        get() = fileFilterState.value
        set(value) {
            fileFilterState.value = value
        }

    var sortOption: ManageFilesSortOption
        get() = sortOptionState.value
        set(value) {
            sortOptionState.value = value
        }

    fun onFilterSelected(newFilter: ManageFilesFilter) {
        val currentFilter = fileFilter
        if (newFilter == currentFilter) return
        listScrollAnchorsState.value = listScrollAnchorsState.value + (
            currentFilter to ScrollAnchor(
                index = listModeState.firstVisibleItemIndex,
                offset = listModeState.firstVisibleItemScrollOffset
            )
        )
        explorerScrollAnchorsState.value = explorerScrollAnchorsState.value + (
            currentFilter to ScrollAnchor(
                index = explorerModeState.firstVisibleItemIndex,
                offset = explorerModeState.firstVisibleItemScrollOffset
            )
        )
        fileFilter = newFilter
    }

    fun listScrollAnchorForCurrentFilter(): Pair<Int, Int> {
        val anchor = listScrollAnchorsState.value[fileFilter] ?: ScrollAnchor(index = 0, offset = 0)
        return anchor.index to anchor.offset
    }

    fun explorerScrollAnchorForCurrentFilter(): Pair<Int, Int> {
        val anchor = explorerScrollAnchorsState.value[fileFilter] ?: ScrollAnchor(index = 0, offset = 0)
        return anchor.index to anchor.offset
    }

    fun applyPreparedData(
        prep: PreparedFilesDl,
        initialExpandedDirs: Set<String>,
        contentKey: String
    ) {
        val sameContent = contentKeyState.value == contentKey
        val oldCheckedByPath: Set<String> = if (sameContent) {
            orderedForSave.withIndex()
                .filter { (idx, _) -> checks.getOrNull(idx) == true }
                .map { (_, file) -> file.innerPath }
                .toSet()
        } else {
            emptySet()
        }

        orderedForSave = prep.ordered.map { it.file }
        selectableForSave = prep.selectableIndices
        checks = prep.initialChecks.mapIndexed { idx, initial ->
            val path = prep.ordered.getOrNull(idx)?.file?.innerPath
            initial || (path != null && path in oldCheckedByPath)
        }
        if (!sameContent) {
            expandedDirs = initialExpandedDirs
        }
        contentKeyState.value = contentKey
    }
}

@Composable
fun rememberManageFilesState(): ManageFilesState {
    val listModeState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val explorerModeState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val fileQueryState = rememberSaveable { mutableStateOf("") }
    val checksState = remember { mutableStateOf(listOf<Boolean>()) }
    val orderedForSaveState = remember { mutableStateOf(emptyList<TorrentFile>()) }
    val selectableForSaveState = remember { mutableStateOf(emptyList<Int>()) }
    val showSaveDialogState = remember { mutableStateOf(false) }
    val removeCandidateState = remember { mutableStateOf<RemoveCandidate?>(null) }
    val bulkRemoveCandidatesState = remember { mutableStateOf(emptyList<RemoveCandidate>()) }
    val alsoDeleteState = remember { mutableStateOf(false) }
    val viewModeState = rememberSaveable { mutableStateOf(FilesViewMode.LIST) }
    val expandedDirsState = remember { mutableStateOf(emptySet<String>()) }
    val fileFilterState = rememberSaveable { mutableStateOf(ManageFilesFilter.ALL) }
    val sortOptionState = rememberSaveable { mutableStateOf(ManageFilesSortOption.RELEVANCE) }
    val listScrollAnchorsState = remember { mutableStateOf(emptyMap<ManageFilesFilter, ScrollAnchor>()) }
    val explorerScrollAnchorsState = remember { mutableStateOf(emptyMap<ManageFilesFilter, ScrollAnchor>()) }
    val contentKeyState = remember { mutableStateOf<String?>(null) }

    return remember(
        listModeState,
        explorerModeState,
        fileQueryState,
        checksState,
        orderedForSaveState,
        selectableForSaveState,
        showSaveDialogState,
        removeCandidateState,
        bulkRemoveCandidatesState,
        alsoDeleteState,
        viewModeState,
        expandedDirsState,
        fileFilterState,
        sortOptionState,
        listScrollAnchorsState,
        explorerScrollAnchorsState,
        contentKeyState
    ) {
        ManageFilesState(
            listModeState = listModeState,
            explorerModeState = explorerModeState,
            fileQueryState = fileQueryState,
            checksState = checksState,
            orderedForSaveState = orderedForSaveState,
            selectableForSaveState = selectableForSaveState,
            showSaveDialogState = showSaveDialogState,
            removeCandidateState = removeCandidateState,
            bulkRemoveCandidatesState = bulkRemoveCandidatesState,
            alsoDeleteState = alsoDeleteState,
            viewModeState = viewModeState,
            expandedDirsState = expandedDirsState,
            fileFilterState = fileFilterState,
            sortOptionState = sortOptionState,
            listScrollAnchorsState = listScrollAnchorsState,
            explorerScrollAnchorsState = explorerScrollAnchorsState,
            contentKeyState = contentKeyState
        )
    }
}
