package com.psycode.spotiflac.domain.usecase.notifications

import com.psycode.spotiflac.domain.model.NotificationPreferences
import com.psycode.spotiflac.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNotificationPreferencesUseCase @Inject constructor(
    private val repository: NotificationPreferencesRepository
) {
    operator fun invoke(): Flow<NotificationPreferences> = repository.observeNotificationPreferences()
}

