package com.psycode.spotiflac.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.mode.AppModeCodec
import com.psycode.spotiflac.domain.repository.AppModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreAppModeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AppModeRepository {

    private object Keys {
        val APP_MODE = stringPreferencesKey("app_mode")
    }

    override fun observeMode(): Flow<AppMode> =
        dataStore.data.map { prefs ->
            val raw = prefs[Keys.APP_MODE]
            AppModeCodec.decode(raw)
        }

    override suspend fun setMode(mode: AppMode) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_MODE] = AppModeCodec.encode(mode)
        }
    }

    override suspend fun clearMode() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.APP_MODE)
        }
    }
}
