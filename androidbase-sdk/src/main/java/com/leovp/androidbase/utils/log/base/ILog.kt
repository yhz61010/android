package com.leovp.androidbase.utils.log.base

import com.leovp.androidbase.exts.kotlin.ITAG

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

    // ========================================================
    // ===== Auto TAG version
    // ========================================================

    fun v(message: String?) = v(ITAG, message)
    fun d(message: String?) = d(ITAG, message)
    fun i(message: String?) = i(ITAG, message)
    fun w(message: String?) = w(ITAG, message)
    fun e(message: String?) = e(ITAG, message)
    fun e(throwable: Throwable?) = e(ITAG, throwable)
    fun f(message: String?) = f(ITAG, message)

    fun v(message: String?, throwable: Throwable?) = v(ITAG, message, throwable)
    fun d(message: String?, throwable: Throwable?) = d(ITAG, message, throwable)
    fun i(message: String?, throwable: Throwable?) = i(ITAG, message, throwable)
    fun w(message: String?, throwable: Throwable?) = w(ITAG, message, throwable)
    fun f(message: String?, throwable: Throwable?) = f(ITAG, message, throwable)

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