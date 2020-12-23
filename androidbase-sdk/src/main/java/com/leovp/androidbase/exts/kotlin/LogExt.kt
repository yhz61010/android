package com.leovp.androidbase.exts.kotlin

import com.leovp.androidbase.utils.log.LogContext

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:39
 */
fun fail(message: String): Nothing {
    throw IllegalArgumentException(message)
}

fun exception(message: String): Nothing {
    throw Exception(message)
}

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
    LogContext.log.v("$ITAG-str", this)
}

fun String.dLog() {
    LogContext.log.d("$ITAG-str", this)
}

fun String.iLog() {
    LogContext.log.i("$ITAG-str", this)
}

fun String.wLog() {
    LogContext.log.w("$ITAG-str", this)
}

fun String.fLog() {
    LogContext.log.e("$ITAG-str", this)
}