package com.leovp.mvvm

/**
 * Author: Michael Leo
 * Date: 2025/3/21 13:42
 */
object CrashHandler {
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun initCrashHandler(customExceptionHandler: Thread.UncaughtExceptionHandler? = null) {
        defaultExceptionHandler = customExceptionHandler ?: Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)
    }
}
