package com.leovp.log_sdk.base

import com.leovp.log_sdk.LogContext

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:39
 */

fun exceptionLog(message: String): Nothing {
    error(message)
}

val Any.ITAG: String
    get() {
        val tag = javaClass.simpleName
        return if (tag.length <= 23) tag else tag.substring(0, 23)
    }

// ==============================

fun String.vLog() {
    LogContext.log.v(this)
}

fun String.dLog() {
    LogContext.log.d(this)
}

fun String.iLog() {
    LogContext.log.i(this)
}

fun String.wLog() {
    LogContext.log.w(this)
}

fun String.fLog() {
    LogContext.log.e(this)
}