package com.psycode.spotiflac.ui.component.managefiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageFilesSelectionReducersTest {

    @Test
    fun `toggleFileSelection updates only target index`() {
        val checks = listOf(false, false, true)

        val updated = toggleFileSelection(checks, index = 1, toChecked = true)

        assertEquals(listOf(false, true, true), updated)
    }

    @Test
    fun `toggleFileSelection returns same instance for invalid index or same value`() {
        val checks = listOf(true, false)

        val sameValue = toggleFileSelection(checks, index = 0, toChecked = true)
        val invalid = toggleFileSelection(checks, index = 10, toChecked = true)

        assertSame(checks, sameValue)
        assertSame(checks, invalid)
    }

    @Test
    fun `areAllSelectableChecked handles empty and partial selection`() {
        assertFalse(areAllSelectableChecked(checks = listOf(true), selectableIndices = emptyList()))
        assertFalse(areAllSelectableChecked(checks = listOf(true, false), selectableIndices = listOf(0, 1)))
        assertTrue(areAllSelectableChecked(checks = listOf(true, true), selectableIndices = listOf(0, 1)))
    }

    @Test
    fun `selectedSelectableCount counts only selectable true values`() {
        val count = selectedSelectableCount(
            checks = listOf(true, false, true, true),
            selectableIndices = listOf(0, 1, 3, 99)
        )
        assertEquals(2, count)
    }

    @Test
    fun `selectedSelectableSizeBytes sums selected sizes for valid selectable indices`() {
        val size = selectedSelectableSizeBytes(
            checks = listOf(true, false, true, true),
            selectableIndices = listOf(0, 1, 3, 99),
            sizesByIndex = listOf(100L, 200L, 300L, 400L)
        )
        assertEquals(500L, size)
    }

    @Test
    fun `toggleAllSelectable flips target state and skips out of bounds`() {
        val checks = listOf(true, false, false)
        val selectable = listOf(0, 1, 5)

        val turnedOn = toggleAllSelectable(checks = checks, selectableIndices = selectable)
        assertEquals(listOf(true, true, false), turnedOn)

        val turnedOff = toggleAllSelectable(checks = turnedOn, selectableIndices = selectable)
        assertEquals(listOf(false, false, false), turnedOff)
    }

    @Test
    fun `toggleAllSelectable returns same instance when nothing can change`() {
        val checks = listOf(true)
        val unchangedEmpty = toggleAllSelectable(checks, selectableIndices = emptyList())
        val unchangedInvalid = toggleAllSelectable(checks, selectableIndices = listOf(10))
        assertSame(checks, unchangedEmpty)
        assertSame(checks, unchangedInvalid)
    }
}
