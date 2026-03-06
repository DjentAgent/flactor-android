package com.psycode.spotiflac.ui.screen.trackdetail

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandableSectionPagingTest {

    @Test
    fun `expandableTotalPages handles empty and non-empty inputs`() {
        assertEquals(1, expandableTotalPages(totalItems = 0, pageSize = 18))
        assertEquals(1, expandableTotalPages(totalItems = 10, pageSize = 18))
        assertEquals(2, expandableTotalPages(totalItems = 19, pageSize = 18))
    }

    @Test
    fun `expandableNormalizePage clamps to valid range`() {
        assertEquals(0, expandableNormalizePage(page = -5, totalItems = 20, pageSize = 18))
        assertEquals(1, expandableNormalizePage(page = 5, totalItems = 20, pageSize = 18))
    }

    @Test
    fun `expandablePageSlice returns proper sublist`() {
        val items = (1..40).toList()
        assertEquals((1..18).toList(), expandablePageSlice(items, page = 0, pageSize = 18))
        assertEquals((19..36).toList(), expandablePageSlice(items, page = 1, pageSize = 18))
        assertEquals((37..40).toList(), expandablePageSlice(items, page = 2, pageSize = 18))
        assertEquals(emptyList<Int>(), expandablePageSlice(items, page = 3, pageSize = 18))
    }
}
