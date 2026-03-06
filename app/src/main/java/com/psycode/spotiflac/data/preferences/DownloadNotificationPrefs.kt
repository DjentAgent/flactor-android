package com.psycode.spotiflac.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.psycode.spotiflac.domain.model.NotificationPreferences

object DownloadNotificationPrefs {
    const val KEY_PROGRESS_NOTIFICATIONS_ENABLED = "download_progress_notifications_enabled"
    const val KEY_EVENT_NOTIFICATIONS_ENABLED = "download_event_notifications_enabled"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    fun read(sharedPreferences: SharedPreferences): NotificationPreferences =
        NotificationPreferences(
            progressEnabled = sharedPreferences.getBoolean(KEY_PROGRESS_NOTIFICATIONS_ENABLED, true),
            eventsEnabled = sharedPreferences.getBoolean(KEY_EVENT_NOTIFICATIONS_ENABLED, true)
        )
}

