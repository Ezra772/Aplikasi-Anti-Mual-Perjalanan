package com.ezra.motioncues.utils

import android.util.Log
import com.ezra.motioncues.BuildConfig

/**
 * Thin logging facade that gates debug/warning output behind [BuildConfig.DEBUG].
 *
 * Error logs are always emitted regardless of build type so that crash reports
 * in production contain useful diagnostic information.
 *
 * Usage:
 *   Logger.d(TAG, "Something happened")
 *   Logger.e(TAG, "Fatal error", exception)
 */
object Logger {

    private val isDebug: Boolean = BuildConfig.DEBUG

    /**
     * Log a debug message. Only emitted in debug builds.
     */
    fun d(tag: String, msg: String) {
        if (isDebug) {
            Log.d(tag, msg)
        }
    }

    /**
     * Log an error. Always emitted, optionally with a [Throwable].
     */
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, msg, throwable)
        } else {
            Log.e(tag, msg)
        }
    }

    /**
     * Log a warning. Only emitted in debug builds.
     */
    fun w(tag: String, msg: String) {
        if (isDebug) {
            Log.w(tag, msg)
        }
    }
}
