package com.leovp.log.base

import android.R.attr.tag
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:39
 *
 * Usage:
 * ```
 * d(TAG) { "=====> Enter MainActivity <=====" }
 *
 * w {
 *     tag = TAG
 *     this.outputType = outputType
 *     this.message = message
 * }
 * ```
 *
 * ```kotlin
 * inline fun d(crossinline config: LogConfig4Debug.() -> Unit) {
 *     val logConfig = LogConfig4Debug().apply(config)
 *     @Suppress("SENSELESS_COMPARISON") if (BuildConfig.DEBUG) {
 *         val ret = logConfig.block()
 *         if (ret is String?) {
 *             LogContext.log.d(
 *                 tag = logConfig.tag,
 *                 message = ret,
 *                 fullOutput = logConfig.fullOutput,
 *                 throwable = logConfig.throwable,
 *                 outputType = logConfig.outputType
 *             )
 *         }
 *     }
 * }
 *
 * inline fun d(tag: String, crossinline block: () -> Any?) {
 *     d {
 *         this.tag = tag
 *         this.block = { block() }
 *     }
 * }
 * ```
 */

val Any.ITAG: String
    get() {
        return javaClass.simpleName.take(23)
    }

interface ILogConfig {
    var tag: String
    var throwable: Throwable?
    var fullOutput: Boolean
    var outputType: LogOutType
}

class LogConfig : ILogConfig {
    override var tag: String = ""
    override var throwable: Throwable? = null
    override var fullOutput: Boolean = false
    override var outputType: LogOutType = LogOutType.COMMON
    var message: String? = null
}

@Suppress("unused")
class LogConfig4Debug : ILogConfig {
    override var tag: String = ""
    override var throwable: Throwable? = null
    override var fullOutput: Boolean = false
    override var outputType: LogOutType = LogOutType.COMMON
    lateinit var block: () -> Any?
}

inline fun i(crossinline config: LogConfig.() -> Unit) {
    val logConfig = LogConfig().apply(config)
    LogContext.log.i(
        tag = logConfig.tag,
        message = logConfig.message,
        fullOutput = logConfig.fullOutput,
        throwable = logConfig.throwable,
        outputType = logConfig.outputType
    )
}

inline fun i(tag: String, crossinline message: () -> String?) {
    i {
        this.tag = tag
        this.message = message()
    }
}

inline fun w(crossinline config: LogConfig.() -> Unit) {
    val logConfig = LogConfig().apply(config)
    LogContext.log.w(
        tag = logConfig.tag,
        message = logConfig.message,
        fullOutput = logConfig.fullOutput,
        throwable = logConfig.throwable,
        outputType = logConfig.outputType
    )
}

inline fun w(tag: String, crossinline message: () -> String?) {
    w {
        this.tag = tag
        this.message = message()
    }
}

inline fun e(crossinline config: LogConfig.() -> Unit) {
    val logConfig = LogConfig().apply(config)
    LogContext.log.e(
        tag = logConfig.tag,
        message = logConfig.message,
        fullOutput = logConfig.fullOutput,
        throwable = logConfig.throwable,
        outputType = logConfig.outputType
    )
}

inline fun e(tag: String, throwable: Throwable? = null, crossinline message: () -> String?) {
    e {
        this.tag = tag
        this.throwable = throwable
        this.message = message()
    }
}
