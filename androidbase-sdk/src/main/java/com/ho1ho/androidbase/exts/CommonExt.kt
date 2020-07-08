package com.ho1ho.androidbase.exts

import com.ho1ho.androidbase.utils.LLog

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

fun Any.verbLog(msg: String) {
    LLog.v("$ITAG-auto", msg)
}

fun Any.debugLog(msg: String) {
    LLog.d("$ITAG-auto", msg)
}

fun Any.infoLog(msg: String) {
    LLog.i("$ITAG-auto", msg)
}

fun Any.warnLog(msg: String) {
    LLog.w("$ITAG-auto", msg)
}

fun Any.errorLog(msg: String) {
    LLog.e("$ITAG-auto", msg)
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