package com.leovp.androidbase.utils.log

import android.util.Log
import com.leovp.androidbase.utils.log.base.ILog

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
class LLog : ILog {
    companion object {
        private const val BASE_TAG = "LEO-"
    }

    override fun getTagName(tag: String): String {
        return "$BASE_TAG$tag"
    }

    @Suppress("unused")
    override fun v(tag: String, message: String?) {
        Log.v(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    override fun d(tag: String, message: String?) {
        Log.d(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    override fun i(tag: String, message: String?) {
        Log.i(getTagName(tag), message ?: "[null]")
    }

    override fun w(tag: String, message: String?) {
        Log.w(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, message: String?) {
        Log.e(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    override fun e(tag: String, throwable: Throwable?) {
        e(tag, null, throwable)
    }

    @Suppress("unused")
    override fun f(tag: String, message: String?) {
        Log.wtf(getTagName(tag), message)
    }

    @Suppress("unused")
    override fun v(tag: String, message: String?, throwable: Throwable?) {
        Log.v(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    override fun d(tag: String, message: String?, throwable: Throwable?) {
        Log.d(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    override fun i(tag: String, message: String?, throwable: Throwable?) {
        Log.i(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    override fun w(tag: String, message: String?, throwable: Throwable?) {
        Log.w(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    override fun e(tag: String, message: String?, throwable: Throwable?) {
        Log.e(getTagName(tag), getMessage(message, throwable))
    }

    @Suppress("unused")
    override fun f(tag: String, message: String?, throwable: Throwable?) {
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
}