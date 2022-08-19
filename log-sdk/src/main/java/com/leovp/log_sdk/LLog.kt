package com.leovp.log_sdk

import android.util.Log
import com.leovp.log_sdk.base.ILog

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
class LLog(private val prefix: String) : ILog {
    override fun getTagName(tag: String) = "$prefix-$tag"

    override var enableLog: Boolean = true

    override fun printVerbLog(tag: String, message: String, outputType: Int) {
        Log.v(tag, if (outputType == -1) message else "[$outputType]$message")
    }

    override fun printDebugLog(tag: String, message: String, outputType: Int) {
        Log.d(tag, if (outputType == -1) message else "[$outputType]$message")
    }

    override fun printInfoLog(tag: String, message: String, outputType: Int) {
        Log.i(tag, if (outputType == -1) message else "[$outputType]$message")
    }

    override fun printWarnLog(tag: String, message: String, outputType: Int) {
        Log.w(tag, if (outputType == -1) message else "[$outputType]$message")
    }

    override fun printErrorLog(tag: String, message: String, outputType: Int) {
        Log.e(tag, if (outputType == -1) message else "[$outputType]$message")
    }

    override fun printFatalLog(tag: String, message: String, outputType: Int) {
        Log.wtf(tag, if (outputType == -1) message else "[$outputType]$message")
    }
}
