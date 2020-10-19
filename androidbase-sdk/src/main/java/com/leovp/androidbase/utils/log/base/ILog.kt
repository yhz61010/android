package com.leovp.androidbase.utils.log.base

/**
 * Author: Michael Leo
 * Date: 2020/10/16 下午5:33
 */
interface ILog {
    fun getTagName(tag: String): String

    fun v(tag: String, message: String?)
    fun d(tag: String, message: String?)
    fun i(tag: String, message: String?)
    fun w(tag: String, message: String?)
    fun e(tag: String, message: String?)
    fun e(tag: String, throwable: Throwable?)
    fun f(tag: String, message: String?)

    fun v(tag: String, message: String?, throwable: Throwable?)
    fun d(tag: String, message: String?, throwable: Throwable?)
    fun i(tag: String, message: String?, throwable: Throwable?)
    fun w(tag: String, message: String?, throwable: Throwable?)
    fun e(tag: String, message: String?, throwable: Throwable?)
    fun f(tag: String, message: String?, throwable: Throwable?)

    @Suppress("unused")
    fun getStackTraceString(tr: Throwable?): String {
        return android.util.Log.getStackTraceString(tr)
    }

    @Suppress("unused")
    // Usage: getStackTraceString(Thread.currentThread().getStackTrace())
    fun getStackTraceString(elements: Array<StackTraceElement>?): String {
        if (elements.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        elements.forEach { sb.append('\n').append(it.toString()) }
        return sb.toString()
    }
}