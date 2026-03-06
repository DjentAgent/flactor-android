package com.psycode.spotiflac.data.service.download.orchestration

import com.psycode.spotiflac.data.service.download.notification.AppNotificationManager
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class DownloadCommandHandler @Inject constructor(
    private val stateStore: DownloadStateStore,
    private val notificationManager: AppNotificationManager
) {
    suspend fun pause(taskId: String): Boolean {
        DownloadLog.d("Command pause taskId=$taskId")
        val updated = stateStore.updateTaskIfAndFlush(
            taskId = taskId,
            predicate = { canPause(it.status) },
            transform = { current -> applyPauseTransition(current) ?: current }
        )
        DownloadLog.t(
            scope = "Cmd",
            message = "pause taskId=$taskId applied=${updated != null} resultingStatus=${updated?.status}"
        )
        return updated != null
    }

    suspend fun resume(taskId: String): Boolean {
        DownloadLog.d("Command resume taskId=$taskId")
        val updated = stateStore.updateTaskIfAndFlush(
            taskId = taskId,
            predicate = { canResume(it.status) },
            transform = { current ->
                applyResumeTransition(current, nowMs = System.currentTimeMillis()) ?: current
            }
        )
        if (updated != null) {
            notificationManager.cancelTaskEventNotifications(taskId)
        }
        DownloadLog.t(
            scope = "Cmd",
            message = "resume taskId=$taskId applied=${updated != null} resultingStatus=${updated?.status}"
        )
        return updated != null
    }

    suspend fun cancel(taskId: String): Boolean {
        DownloadLog.d("Command cancel taskId=$taskId")
        val updated = stateStore.updateTaskIfAndFlush(
            taskId = taskId,
            predicate = { true },
            transform = ::applyCancelTransition
        )
        if (updated != null) {
            notificationManager.cancelTaskNotification(taskId)
            notificationManager.cancelTaskEventNotifications(taskId)
        }
        DownloadLog.t(
            scope = "Cmd",
            message = "cancel taskId=$taskId applied=${updated != null} resultingStatus=${updated?.status}"
        )
        return updated != null
    }
}




