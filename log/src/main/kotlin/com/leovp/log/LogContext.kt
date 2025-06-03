package com.leovp.log

import com.leovp.log.base.AbsLog
import com.leovp.log.base.LogLevel

/**
 *  You can implement your log wrapper by implement `ILog` or use the default log wrapper `AbsLog`.
 *
 * `LLog` is the wrapper of Android default log.
 *
 * After implementing your log wrapper,
 * you must initialize it ASAP by calling `LogContext.setLogImpl()`:
 *
 * Assume that `CLog` is your custom log wrapper.
 * ```kotlin
 * LogContext.setLogImpl(
 *     CLog(tagPrefix = TAG_PREFIX, logLevel = LogLevel.VERB, enableLog = true).apply {
 *         init(context)
 *     }
 * )
 * ```
 *
 * The default implementation is like this below:
 * ```kotlin
 * LogContext.setLogImpl(
 *     LLog(tagPrefix = TAG_PREFIX, logLevel = LogLevel.VERB, enableLog = true)
 * )
 * ```
 *
 * For convenience and the best performance, you'd better write some extensions, so that you can
 * omit the log codes in release mode:
 *
 * ```kotlin
 * inline fun d(
 *     tag: String = "",
 *     throwable: Throwable? = null,
 *     fullOutput: Boolean = false,
 *     outputType: LogOutType = LogOutType.COMMON,
 *     generateMsg: () -> Any?
 * ) {
 *     // The 'if' condition must be a static final boolean value.
 *     if (BuildConfig.DEBUG) {
 *         val ret = generateMsg()
 *         if (ret is String?) {
 *             LogContext.log.d(
 *                 tag = tag,
 *                 message = ret,
 *                 fullOutput = fullOutput,
 *                 throwable = throwable,
 *                 outputType = outputType
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * Author: Michael Leo
 * Date: 2020/10/16 下午6:15
 */
object LogContext {
    // var enableLog = true
    //     set(value) {
    //         field = value
    //         if (!LogContext::log.isInitialized) throw IllegalAccessException("You must call setLogImp() first")
    //         log.enableLog = value
    //     }

    lateinit var log: AbsLog
        private set

    @Suppress("unused")
    val logLevel: LogLevel
        get() = if (isLogInitialized()) {
            log.logLevel
        } else {
            throw IllegalAccessException("You must call setLogImp() first")
        }

    fun isLogInitialized(): Boolean = ::log.isInitialized

    fun setLogImpl(log: AbsLog) {
        LogContext.log = log
    }
}
