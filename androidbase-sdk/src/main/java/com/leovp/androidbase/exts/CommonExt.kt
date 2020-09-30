package com.leovp.androidbase.exts

import com.leovp.androidbase.utils.LLog

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:39
 */
fun fail(message: String): Nothing {
    throw IllegalStateException(message)
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
    LLog.v("$ITAG-str", this)
}

fun String.dLog() {
    LLog.d("$ITAG-str", this)
}

fun String.iLog() {
    LLog.i("$ITAG-str", this)
}

fun String.wLog() {
    LLog.w("$ITAG-str", this)
}

fun String.fLog() {
    LLog.e("$ITAG-str", this)
}