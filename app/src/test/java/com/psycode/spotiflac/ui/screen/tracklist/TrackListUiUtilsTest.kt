package com.psycode.spotiflac.ui.screen.tracklist

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackListUiUtilsTest {

    @Test
    fun `resolveTrackListBackAction closes search first`() {
        assertEquals(
            TrackListBackAction.CLOSE_SEARCH,
            resolveTrackListBackAction(searchActive = true, query = "abc")
        )
    }

    @Test
    fun `resolveTrackListBackAction clears query when search inactive`() {
        assertEquals(
            TrackListBackAction.CLEAR_QUERY,
            resolveTrackListBackAction(searchActive = false, query = "abc")
        )
    }

    @Test
    fun `resolveTrackListBackAction navigates back when idle`() {
        assertEquals(
            TrackListBackAction.NAVIGATE_BACK,
            resolveTrackListBackAction(searchActive = false, query = "")
        )
    }
}
