@file:Suppress("unused")

package com.leovp.androidbase.utils

/**
 * Author: Michael Leo
 * Date: 2025/3/21 13:42
 */
object CrashHandler {
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Install a crash handler that runs [customExceptionHandler] (when provided) and then
     * chains to whatever [Thread.UncaughtExceptionHandler] was already installed, so
     * third-party reporters (e.g. Crashlytics, Bugsnag) still receive the crash instead of
     * being silently replaced.
     */
    fun initCrashHandler(customExceptionHandler: Thread.UncaughtExceptionHandler? = null) {
        // Capture the existing handler once so we can chain to it after our own handling.
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // A throwing custom handler must not stop us from chaining to the previous one.
            runCatching { customExceptionHandler?.uncaughtException(thread, throwable) }
            previousExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
}
