package com.psycode.spotiflac.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psycode.spotiflac.domain.model.DefaultSaveLocation
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.repository.SaveLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSaveLocationRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SaveLocationRepository {

    override fun observeDefaultSaveLocation(): Flow<DefaultSaveLocation> =
        dataStore.data.map { prefs ->
            val option = prefs[Keys.DEFAULT_SAVE_OPTION]?.toSaveOptionOrNull()
            val folderUri = prefs[Keys.DEFAULT_CUSTOM_FOLDER_URI]?.trim()?.takeIf { it.isNotEmpty() }
            if (option == SaveOption.CUSTOM_FOLDER) {
                DefaultSaveLocation(saveOption = option, customFolderUri = folderUri)
            } else {
                DefaultSaveLocation(saveOption = option, customFolderUri = null)
            }
        }

    override suspend fun setDefaultSaveLocation(saveOption: SaveOption, customFolderUri: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SAVE_OPTION] = saveOption.name
            if (saveOption == SaveOption.CUSTOM_FOLDER && !customFolderUri.isNullOrBlank()) {
                prefs[Keys.DEFAULT_CUSTOM_FOLDER_URI] = customFolderUri
            } else {
                prefs.remove(Keys.DEFAULT_CUSTOM_FOLDER_URI)
            }
        }
    }

    private fun String.toSaveOptionOrNull(): SaveOption? = runCatching {
        SaveOption.valueOf(this)
    }.getOrNull()

    private object Keys {
        val DEFAULT_SAVE_OPTION = stringPreferencesKey("default_save_option")
        val DEFAULT_CUSTOM_FOLDER_URI = stringPreferencesKey("default_custom_folder_uri")
    }
}

