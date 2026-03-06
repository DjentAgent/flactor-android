package com.psycode.spotiflac.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psycode.spotiflac.domain.model.NotificationPreferences
import com.psycode.spotiflac.domain.usecase.notifications.ObserveNotificationPreferencesUseCase
import com.psycode.spotiflac.domain.usecase.notifications.SetEventNotificationsEnabledUseCase
import com.psycode.spotiflac.domain.usecase.notifications.SetProgressNotificationsEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    observeNotificationPreferences: ObserveNotificationPreferencesUseCase,
    private val setProgressNotificationsEnabled: SetProgressNotificationsEnabledUseCase,
    private val setEventNotificationsEnabled: SetEventNotificationsEnabledUseCase
) : ViewModel() {

    val notificationPreferences: StateFlow<NotificationPreferences> =
        observeNotificationPreferences().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationPreferences()
        )

    fun setProgressEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setProgressNotificationsEnabled(enabled)
        }
    }

    fun setEventsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setEventNotificationsEnabled(enabled)
        }
    }
}

