package com.ho1ho.androidbase.exts

import com.ho1ho.androidbase.utils.CLog

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
    CLog.v("$ITAG-auto", msg)
}

fun Any.debugLog(msg: String) {
    CLog.d("$ITAG-auto", msg)
}

fun Any.infoLog(msg: String) {
    CLog.i("$ITAG-auto", msg)
}

fun Any.warnLog(msg: String) {
    CLog.w("$ITAG-auto", msg)
}

fun Any.errorLog(msg: String) {
    CLog.e("$ITAG-auto", msg)
}

// ==============================

fun String.vLog() {
    CLog.v("$ITAG-str", this)
}

fun String.dLog() {
    CLog.d("$ITAG-str", this)
}

fun String.iLog() {
    CLog.i("$ITAG-str", this)
}

fun String.wLog() {
    CLog.w("$ITAG-str", this)
}

fun String.fLog() {
    CLog.e("$ITAG-str", this)
}