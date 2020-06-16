package com.ho1ho.androidbase.utils

import android.util.Log

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
object LLog {
    private const val BASE_TAG = "LEO-"

    private fun getTagName(tag: String): String {
        return BASE_TAG + tag
    }

    @Suppress("unused")
    fun v(tag: String, message: String?) {
        Log.v(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun d(tag: String, message: String?) {
        Log.d(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun i(tag: String, message: String?) {
        Log.i(getTagName(tag), message ?: "[null]")
    }

    fun w(tag: String, message: String?) {
        Log.w(getTagName(tag), message ?: "[null]")
    }

    fun e(tag: String, message: String?) {
        Log.e(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun e(tag: String, throwable: Throwable?) {
        e(tag, null, throwable)
    }

    @Suppress("unused")
    fun f(tag: String, message: String?) {
        Log.wtf(getTagName(tag), message)
    }

    @Suppress("unused")
    fun v(tag: String, message: String?, throwable: Throwable?) {
        Log.v(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    fun d(tag: String, message: String?, throwable: Throwable?) {
        Log.d(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    fun i(tag: String, message: String?, throwable: Throwable?) {
        Log.i(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    fun w(tag: String, message: String?, throwable: Throwable?) {
        Log.w(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    fun e(tag: String, message: String?, throwable: Throwable?) {
        Log.e(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    fun f(tag: String, message: String?, throwable: Throwable?) {
        Log.wtf(getTagName(tag), getMessage(message, throwable))
    }

    private fun getMessage(message: String?, throwable: Throwable?): String {
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

    @Suppress("WeakerAccess")
    fun getStackTraceString(tr: Throwable?): String {
        return Log.getStackTraceString(tr)
    }

    // Usage: getStackTraceString(Thread.currentThread().getStackTrace())
    @Suppress("unused")
    fun getStackTraceString(elements: Array<StackTraceElement>?): String {
        if (elements.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        elements.forEach { sb.append('\n').append(it.toString()) }
        return sb.toString()
    }
}