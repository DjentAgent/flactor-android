package com.psycode.spotiflac.domain.repository

import com.psycode.spotiflac.domain.model.NotificationPreferences
import kotlinx.coroutines.flow.Flow

interface NotificationPreferencesRepository {
    fun observeNotificationPreferences(): Flow<NotificationPreferences>

    suspend fun setProgressNotificationsEnabled(enabled: Boolean)

    suspend fun setEventNotificationsEnabled(enabled: Boolean)
}

