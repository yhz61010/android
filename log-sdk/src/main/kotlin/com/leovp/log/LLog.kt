package com.leovp.log

import android.util.Log
import com.leovp.log.base.AbsLog

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
class LLog(
    tagPrefix: String,
    private val enableLog: Boolean = true,
    override var logLevel: LogLevel,
) : AbsLog(tagPrefix) {

    override fun printVerbLog(tag: String, message: String, outputType: Int) {
        if (LogLevel.VERB >= logLevel && enableLog) {
            Log.v(tag, if (outputType == -1) message else "[$outputType]$message")
        }
    }

    override fun printDebugLog(tag: String, message: String, outputType: Int) {
        if (LogLevel.DEBUG >= logLevel && enableLog) {
            Log.d(tag, if (outputType == -1) message else "[$outputType]$message")
        }
    }

    override fun printInfoLog(tag: String, message: String, outputType: Int) {
        if (LogLevel.INFO >= logLevel && enableLog) {
            Log.i(tag, if (outputType == -1) message else "[$outputType]$message")
        }
    }

    override fun printWarnLog(tag: String, message: String, outputType: Int) {
        if (LogLevel.WARN >= logLevel && enableLog) {
            Log.w(tag, if (outputType == -1) message else "[$outputType]$message")
        }
    }

    override fun printErrorLog(tag: String, message: String, outputType: Int) {
        if (LogLevel.ERROR >= logLevel && enableLog) {
            Log.e(tag, if (outputType == -1) message else "[$outputType]$message")
        }
    }

    override fun printFatalLog(tag: String, message: String, outputType: Int) {
        if (LogLevel.FATAL >= logLevel && enableLog) {
            Log.wtf(tag, if (outputType == -1) message else "[$outputType]$message")
        }
    }
}
