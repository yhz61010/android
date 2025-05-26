package com.leovp.demo.basiccomponents.examples.log

import com.leovp.demo.BuildConfig
import com.leovp.log.LogContext
import com.leovp.log.base.LogConfig4Debug

/**
 * Author: Michael Leo
 * Date: 2023/9/19 16:08
 */

inline fun d(crossinline config: LogConfig4Debug.() -> Unit) {
    val logConfig = LogConfig4Debug().apply(config)
    @Suppress("SENSELESS_COMPARISON")
    if (BuildConfig.DEBUG) {
        val ret = logConfig.block()
        if (ret is String?) {
            LogContext.log.d(
                tag = logConfig.tag,
                message = ret,
                fullOutput = logConfig.fullOutput,
                throwable = logConfig.throwable,
                outputType = logConfig.outputType
            )
        }
    }
}

inline fun d(tag: String, crossinline block: () -> Any?) {
    d {
        this.tag = tag
        this.block = { block() }
    }
}
