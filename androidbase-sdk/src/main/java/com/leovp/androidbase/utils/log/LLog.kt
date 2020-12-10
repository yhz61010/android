package com.leovp.androidbase.utils.log

import android.util.Log
import com.leovp.androidbase.utils.log.base.ILog

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
@Suppress("unused")
class LLog : ILog {
    companion object {
        private const val BASE_TAG = "LEO-"
    }

    override fun getTagName(tag: String): String {
        return "$BASE_TAG$tag"
    }

    override fun v(tag: String, message: String?) {
        Log.v(getTagName(tag), message ?: "[null]")
    }

    override fun d(tag: String, message: String?) {
        Log.d(getTagName(tag), message ?: "[null]")
    }

    override fun i(tag: String, message: String?) {
        Log.i(getTagName(tag), message ?: "[null]")
    }

    override fun w(tag: String, message: String?) {
        Log.w(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, message: String?) {
        Log.e(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, throwable: Throwable?) {
        e(tag, null, throwable)
    }

    override fun f(tag: String, message: String?) {
        Log.wtf(getTagName(tag), message)
    }

    override fun v(tag: String, message: String?, throwable: Throwable?) {
        Log.v(getTagName(tag), getMessage(message, throwable))
    }

    override fun d(tag: String, message: String?, throwable: Throwable?) {
        Log.d(getTagName(tag), getMessage(message, throwable))
    }

    override fun i(tag: String, message: String?, throwable: Throwable?) {
        Log.i(getTagName(tag), getMessage(message, throwable))
    }

    override fun w(tag: String, message: String?, throwable: Throwable?) {
        Log.w(getTagName(tag), getMessage(message, throwable))
    }

    override fun e(tag: String, message: String?, throwable: Throwable?) {
        Log.e(getTagName(tag), getMessage(message, throwable))
    }

    override fun f(tag: String, message: String?, throwable: Throwable?) {
        Log.wtf(getTagName(tag), getMessage(message, throwable))
    }
}