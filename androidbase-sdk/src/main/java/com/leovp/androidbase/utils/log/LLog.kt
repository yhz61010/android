package com.leovp.androidbase.utils.log

import android.util.Log
import com.leovp.androidbase.utils.log.base.ILog

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
@Suppress("unused")
class LLog : ILog {

    override fun getTagName(tag: String) = "LEO-$tag"

    override var enableLog: Boolean = true

    override fun v(tag: String, message: String?) {
        if (enableLog) Log.v(getTagName(tag), message ?: "[null]")
    }

    override fun d(tag: String, message: String?) {
        if (enableLog) Log.d(getTagName(tag), message ?: "[null]")
    }

    override fun i(tag: String, message: String?) {
        if (enableLog) Log.i(getTagName(tag), message ?: "[null]")
    }

    override fun w(tag: String, message: String?) {
        if (enableLog) Log.w(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, message: String?) {
        if (enableLog) Log.e(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, throwable: Throwable?) {
        if (enableLog) e(tag, null, throwable)
    }

    override fun f(tag: String, message: String?) {
        if (enableLog) Log.wtf(getTagName(tag), message)
    }

    override fun v(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.v(getTagName(tag), getMessage(message, throwable))
    }

    override fun d(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.d(getTagName(tag), getMessage(message, throwable))
    }

    override fun i(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.i(getTagName(tag), getMessage(message, throwable))
    }

    override fun w(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.w(getTagName(tag), getMessage(message, throwable))
    }

    override fun e(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.e(getTagName(tag), getMessage(message, throwable))
    }

    override fun f(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.wtf(getTagName(tag), getMessage(message, throwable))
    }
}