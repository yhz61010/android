@file:Suppress("unused")

package com.leovp.androidbase.utils

/**
 * Author: Michael Leo
 * Date: 2025/3/21 13:42
 */
object CrashHandler {
    /**
     * Install a crash handler that runs [customExceptionHandler] (when provided) and then
     * chains to whatever [Thread.UncaughtExceptionHandler] was already installed, so
     * third-party reporters (e.g. Crashlytics, Bugsnag) still receive the crash instead of
     * being silently replaced.
     */
    fun initCrashHandler(customExceptionHandler: Thread.UncaughtExceptionHandler? = null) {
        // Capture a local previous handler for this wrapper. Do not store it in a mutable object
        // field, otherwise repeated init calls make older wrappers recursively call themselves.
        val previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // A throwing custom handler must not stop us from chaining to the previous one.
            runCatching { customExceptionHandler?.uncaughtException(thread, throwable) }
            // Symmetrically, a throwing previous handler (e.g. a misbehaving third-party reporter)
            // must not escape this default handler; swallow it so the crash flow stays contained.
            runCatching { previousExceptionHandler?.uncaughtException(thread, throwable) }
        }
    }
}
