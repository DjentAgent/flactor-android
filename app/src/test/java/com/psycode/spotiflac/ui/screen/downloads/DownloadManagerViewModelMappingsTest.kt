package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadManagerViewModelMappingsTest {

    @Test
    fun `resolveRemoveEntrySuccessMessageRes covers all branches`() {
        assertEquals(
            R.string.file_deleted_and_delisted,
            resolveRemoveEntrySuccessMessageRes(alsoDeleteLocal = true, fileDeleted = true)
        )
        assertEquals(
            R.string.could_not_delete_file_but_delisted,
            resolveRemoveEntrySuccessMessageRes(alsoDeleteLocal = true, fileDeleted = false)
        )
        assertEquals(
            R.string.file_delisted,
            resolveRemoveEntrySuccessMessageRes(alsoDeleteLocal = false, fileDeleted = null)
        )
    }

    @Test
    fun `resolveRemoveEntryErrorFallbackRes covers all branches`() {
        assertEquals(
            R.string.file_deleted_delist_error,
            resolveRemoveEntryErrorFallbackRes(alsoDeleteLocal = true, fileDeleted = true)
        )
        assertEquals(
            R.string.could_not_proceed,
            resolveRemoveEntryErrorFallbackRes(alsoDeleteLocal = true, fileDeleted = false)
        )
        assertEquals(
            R.string.could_not_proceed,
            resolveRemoveEntryErrorFallbackRes(alsoDeleteLocal = true, fileDeleted = null)
        )
        assertEquals(
            R.string.could_not_delist,
            resolveRemoveEntryErrorFallbackRes(alsoDeleteLocal = false, fileDeleted = null)
        )
    }

    @Test
    fun `resolveManageFilesLoadErrorMessage uses throwable message with fallback`() {
        assertEquals(
            "boom",
            resolveManageFilesLoadErrorMessage(IllegalStateException("boom"))
        )
        assertEquals(
            "Ошибка загрузки списка файлов",
            resolveManageFilesLoadErrorMessage(IllegalStateException())
        )
    }
}
