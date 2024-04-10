package com.leovp.demo.basiccomponents.examples.log

import com.leovp.demo.BuildConfig
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/9/19 16:08
 */

inline fun d(
    tag: String = "",
    throwable: Throwable? = null,
    fullOutput: Boolean = false,
    outputType: Int = -1,
    generateMsg: () -> Any?
) {
    if (BuildConfig.DEBUG) {
        val ret = generateMsg()
        if (ret is String?) {
            LogContext.log.d(
                tag = tag,
                message = ret,
                fullOutput = fullOutput,
                throwable = throwable,
                outputType = outputType
            )
        }
    }
}

inline fun i(
    tag: String = "",
    throwable: Throwable? = null,
    fullOutput: Boolean = false,
    outputType: Int = -1,
    generateMsg: () -> String?
) {
    LogContext.log.i(
        tag = tag,
        message = generateMsg(),
        fullOutput = fullOutput,
        throwable = throwable,
        outputType = outputType
    )
}

inline fun w(
    tag: String = "",
    throwable: Throwable? = null,
    fullOutput: Boolean = false,
    outputType: Int = -1,
    generateMsg: () -> String?
) {
    LogContext.log.w(
        tag = tag,
        message = generateMsg(),
        fullOutput = fullOutput,
        throwable = throwable,
        outputType = outputType
    )
}

inline fun e(
    tag: String = "",
    throwable: Throwable? = null,
    fullOutput: Boolean = false,
    outputType: Int = -1,
    generateMsg: () -> String?
) {
    LogContext.log.e(
        tag = tag,
        message = generateMsg(),
        fullOutput = fullOutput,
        throwable = throwable,
        outputType = outputType
    )
}
