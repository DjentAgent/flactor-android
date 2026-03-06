package com.psycode.spotiflac.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.psycode.spotiflac.domain.model.NotificationPreferences
import com.psycode.spotiflac.domain.repository.NotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsNotificationPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) : NotificationPreferencesRepository {

    private val prefs = DownloadNotificationPrefs.prefs(context)

    override fun observeNotificationPreferences(): Flow<NotificationPreferences> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == DownloadNotificationPrefs.KEY_PROGRESS_NOTIFICATIONS_ENABLED ||
                key == DownloadNotificationPrefs.KEY_EVENT_NOTIFICATIONS_ENABLED
            ) {
                trySend(readSettings())
            }
        }
        trySend(readSettings())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun setProgressNotificationsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(DownloadNotificationPrefs.KEY_PROGRESS_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    override suspend fun setEventNotificationsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(DownloadNotificationPrefs.KEY_EVENT_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    private fun readSettings(): NotificationPreferences = DownloadNotificationPrefs.read(prefs)
}

