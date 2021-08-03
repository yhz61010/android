package com.leovp.androidbase.utils.log

import android.util.Log
import com.leovp.androidbase.utils.log.base.ILog

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
class LLog(private val prefix: String) : ILog {
    override fun getTagName(tag: String) = "$prefix-$tag"

    override var enableLog: Boolean = true

    override fun printVerbLog(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun printDebugLog(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun printInfoLog(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun printWarnLog(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun printErrorLog(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun printFatalLog(tag: String, message: String) {
        Log.wtf(tag, message)
    }
}