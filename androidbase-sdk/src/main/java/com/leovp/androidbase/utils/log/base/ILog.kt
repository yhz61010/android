package com.leovp.androidbase.utils.log.base

/**
 * Author: Michael Leo
 * Date: 2020/10/16 下午5:33
 */
interface ILog {
    var enableLog: Boolean

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

    fun getMessage(message: String?, throwable: Throwable?): String {
        if (message == null && throwable == null) return "[Empty Message]"

        val sb = StringBuilder()
        if (!message.isNullOrBlank()) {
            sb.append(message)
        }
        if (throwable == null) {
            return sb.toString()
        }
        return if (message == null) {
            sb.append(getStackTraceString(throwable)).toString()
        } else {
            sb.append(" : ").append(getStackTraceString(throwable)).toString()
        }
    }
}