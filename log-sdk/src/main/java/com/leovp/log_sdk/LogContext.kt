package com.leovp.log_sdk

import com.leovp.log_sdk.base.ILog

/**
 *  You can implement your log wrapper by implement `ILog` or else the default log wrapper `LLog` will be used.
 * `LLog` is the wrapper of Android default log.
 *
 * After implementing your log wrapper, you must initialize it in `Application` by calling:
 * Assume that `CLog` is your custom log wrapper.
 * ```kotlin
 * LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
 * ```
 * The default implementation is like this below:
 * ```kotlin
 * LogContext.setLogImp(LLog("LEO"))
 * ```
 *
 * Author: Michael Leo
 * Date: 2020/10/16 下午6:15
 */
object LogContext {
    var enableLog = true
        set(value) {
            field = value
            if (!LogContext::log.isInitialized) throw IllegalAccessException("You must call setLogImp() first")
            log.enableLog = value
        }

    lateinit var log: ILog
        private set

    fun isLogInitialized(): Boolean = ::log.isInitialized

    fun setLogImp(log: ILog) {
        LogContext.log = log
    }
}