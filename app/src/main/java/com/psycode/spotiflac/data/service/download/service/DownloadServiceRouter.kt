package com.psycode.spotiflac.data.service.download.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import javax.inject.Inject

class DownloadServiceRouter @Inject constructor() {
    data class ActionHandlers(
        val onPause: (taskId: String?) -> Unit,
        val onResume: (taskId: String?) -> Unit,
        val onCancel: (taskId: String?) -> Unit,
        val onPauseGroup: (topicId: Int) -> Unit,
        val onResumeGroup: (topicId: Int) -> Unit,
        val onPauseAll: () -> Unit,
        val onResumeAll: () -> Unit,
        val onAppForeground: () -> Unit,
    )

    fun dispatch(
        intent: Intent?,
        handlers: ActionHandlers,
    ) {
        when (intent?.action) {
            ACTION_PAUSE -> handlers.onPause(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_RESUME -> handlers.onResume(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_CANCEL -> handlers.onCancel(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_PAUSE_GROUP -> handlers.onPauseGroup(intent.getIntExtra(EXTRA_TOPIC_ID, -1))
            ACTION_RESUME_GROUP -> handlers.onResumeGroup(intent.getIntExtra(EXTRA_TOPIC_ID, -1))
            ACTION_PAUSE_ALL -> handlers.onPauseAll()
            ACTION_RESUME_ALL -> handlers.onResumeAll()
            ACTION_APP_FOREGROUND -> handlers.onAppForeground()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.psycode.spotiflac.action.PAUSE"
        const val ACTION_RESUME = "com.psycode.spotiflac.action.RESUME"
        const val ACTION_CANCEL = "com.psycode.spotiflac.action.CANCEL"
        const val ACTION_PAUSE_GROUP = "com.psycode.spotiflac.action.PAUSE_GROUP"
        const val ACTION_RESUME_GROUP = "com.psycode.spotiflac.action.RESUME_GROUP"
        const val ACTION_PAUSE_ALL = "com.psycode.spotiflac.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.psycode.spotiflac.action.RESUME_ALL"
        const val ACTION_APP_FOREGROUND = "com.psycode.spotiflac.action.APP_FOREGROUND"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TOPIC_ID = "extra_topic_id"

        fun ensureStarted(context: Context) {
            DownloadLog.d("ensureStarted requested")
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun dispatchTaskAction(
            context: Context,
            action: String,
            taskId: String
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun dispatchGroupAction(
            context: Context,
            action: String,
            topicId: Int
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TOPIC_ID, topicId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun dispatchAppForeground(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = ACTION_APP_FOREGROUND
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
