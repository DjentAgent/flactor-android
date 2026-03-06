package com.psycode.spotiflac.ui.screen.trackdetail

internal fun expandableTotalPages(totalItems: Int, pageSize: Int): Int {
    if (pageSize <= 0) return 1
    if (totalItems <= 0) return 1
    return (totalItems - 1) / pageSize + 1
}

internal fun expandableNormalizePage(
    page: Int,
    totalItems: Int,
    pageSize: Int
): Int {
    val maxPage = (expandableTotalPages(totalItems, pageSize) - 1).coerceAtLeast(0)
    return page.coerceIn(0, maxPage)
}

internal fun <T> expandablePageSlice(
    items: List<T>,
    page: Int,
    pageSize: Int
): List<T> {
    if (pageSize <= 0 || items.isEmpty()) return emptyList()
    val from = (page * pageSize).coerceAtMost(items.size)
    val to = (from + pageSize).coerceAtMost(items.size)
    return if (from < to) items.subList(from, to) else emptyList()
}
