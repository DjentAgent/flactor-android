package com.psycode.spotiflac.ui.component.managefiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ManageFilesFormattersTest {

    @Test
    fun `humanReadableSize formats bytes kb mb gb`() {
        assertEquals("512 B", humanReadableSize(512))
        assertEquals("2 KB", humanReadableSize(2048))
        assertEquals("1.5 MB", humanReadableSize(1572864))
        assertEquals("1.50 GB", humanReadableSize(1610612736))
    }
}
