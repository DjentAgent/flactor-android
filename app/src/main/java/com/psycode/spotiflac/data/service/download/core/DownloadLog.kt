package com.psycode.spotiflac.data.service.download.core

import android.util.Log

internal object DownloadLog {
    const val TAG = "SpotiFlacDownload"
    private const val TRACE_PREFIX = "DL_TRACE"
    private const val VERBOSE_ENABLED = false
    private const val INFO_ENABLED = false
    private const val WARN_ENABLED = true

    fun d(message: String) {
        if (!VERBOSE_ENABLED) return
        runCatching { Log.d(TAG, message) }
            .onFailure { println("$TAG D $message") }
    }

    fun i(message: String) {
        if (!INFO_ENABLED) return
        runCatching { Log.i(TAG, message) }
            .onFailure { println("$TAG I $message") }
    }

    fun w(message: String) {
        if (!WARN_ENABLED) return
        runCatching { Log.w(TAG, message) }
            .onFailure { println("$TAG W $message") }
    }

    fun e(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }.onFailure {
            println("$TAG E $message")
            throwable?.printStackTrace()
        }
    }

    fun t(scope: String, message: String) {
        if (!VERBOSE_ENABLED) return
        runCatching { Log.d(TAG, "$TRACE_PREFIX/$scope $message") }
            .onFailure { println("$TAG D $TRACE_PREFIX/$scope $message") }
    }
}




