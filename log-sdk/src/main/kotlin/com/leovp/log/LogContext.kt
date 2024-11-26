package com.leovp.log

import com.leovp.log.base.AbsLog

/**
 *  You can implement your log wrapper by implement `ILog` or use the default log wrapper `AbsLog`.
 *
 * `LLog` is the wrapper of Android default log.
 *
 * After implementing your log wrapper, you must initialize it ASAP.
 * For example in `Application` by calling `LogContext.setLogImpl()`:
 * Assume that `CLog` is your custom log wrapper.
 * ```kotlin
 * LogContext.setLogImpl(
 *     CLog(tagPrefix = TAG_PREFIX, enableLog = true, logLevel = LogLevel.VERB).apply {
 *         init(this@CustomApplication)
 *     }
 * )
 * ```
 *
 * The default implementation is like this below:
 * ```kotlin
 * LogContext.setLogImpl(
 *     LLog(tagPrefix = "LEO", enableLog = true, logLevel = LogLevel.VERB)
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
 *     outputType: Int = -1,
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
    val logLevel: AbsLog.LogLevel
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
