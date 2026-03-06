package com.psycode.spotiflac.ui.component.managefiles

private fun validSelectableIndices(
    checks: List<Boolean>,
    selectableIndices: List<Int>
): List<Int> = selectableIndices.distinct().filter { it in checks.indices }

fun toggleFileSelection(
    checks: List<Boolean>,
    index: Int,
    toChecked: Boolean
): List<Boolean> {
    if (index !in checks.indices) return checks
    if (checks[index] == toChecked) return checks
    return checks.toMutableList().also { it[index] = toChecked }
}

fun areAllSelectableChecked(
    checks: List<Boolean>,
    selectableIndices: List<Int>
): Boolean {
    val valid = validSelectableIndices(checks = checks, selectableIndices = selectableIndices)
    if (valid.isEmpty()) return false
    return valid.all { idx -> checks[idx] }
}

fun selectedSelectableCount(
    checks: List<Boolean>,
    selectableIndices: List<Int>
): Int {
    val valid = validSelectableIndices(checks = checks, selectableIndices = selectableIndices)
    return valid.count { idx -> checks[idx] }
}

fun selectedSelectableSizeBytes(
    checks: List<Boolean>,
    selectableIndices: List<Int>,
    sizesByIndex: List<Long>
): Long {
    val valid = validSelectableIndices(checks = checks, selectableIndices = selectableIndices)
    return valid.sumOf { idx ->
        if (checks[idx]) sizesByIndex.getOrNull(idx)?.coerceAtLeast(0L) ?: 0L else 0L
    }
}

fun toggleAllSelectable(
    checks: List<Boolean>,
    selectableIndices: List<Int>
): List<Boolean> {
    val valid = validSelectableIndices(checks = checks, selectableIndices = selectableIndices)
    if (valid.isEmpty()) return checks
    val allOn = areAllSelectableChecked(checks = checks, selectableIndices = valid)
    val target = !allOn
    val updated = checks.toMutableList()
    var changed = false
    valid.forEach { idx ->
        if (updated[idx] != target) {
            updated[idx] = target
            changed = true
        }
    }
    return if (changed) updated else checks
}
