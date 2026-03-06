package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.R

internal fun resolveRemoveEntrySuccessMessageRes(
    alsoDeleteLocal: Boolean,
    fileDeleted: Boolean?
): Int = when {
    alsoDeleteLocal && fileDeleted == true -> R.string.file_deleted_and_delisted
    alsoDeleteLocal && fileDeleted == false -> R.string.could_not_delete_file_but_delisted
    else -> R.string.file_delisted
}

internal fun resolveRemoveEntryErrorFallbackRes(
    alsoDeleteLocal: Boolean,
    fileDeleted: Boolean?
): Int = if (alsoDeleteLocal) {
    if (fileDeleted == true) {
        R.string.file_deleted_delist_error
    } else {
        R.string.could_not_proceed
    }
} else {
    R.string.could_not_delist
}

internal fun resolveManageFilesLoadErrorMessage(error: Throwable): String =
    error.message ?: "Ошибка загрузки списка файлов"
