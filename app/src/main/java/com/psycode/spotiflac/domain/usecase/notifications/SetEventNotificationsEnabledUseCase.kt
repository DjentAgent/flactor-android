package com.psycode.spotiflac.domain.usecase.notifications

import com.psycode.spotiflac.domain.repository.NotificationPreferencesRepository
import javax.inject.Inject

class SetEventNotificationsEnabledUseCase @Inject constructor(
    private val repository: NotificationPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setEventNotificationsEnabled(enabled)
    }
}

