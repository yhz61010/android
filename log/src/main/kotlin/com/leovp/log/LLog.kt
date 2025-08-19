package com.leovp.log

import android.util.Log
import com.leovp.log.base.AbsLog
import com.leovp.log.base.LogLevel
import com.leovp.log.base.LogOutType

/**
 * Author: Michael Leo
 * Date: 20-6-15 下午7:16
 */
class LLog(tagPrefix: String, logLevel: LogLevel = LogLevel.VERB, enableLog: Boolean = true) :
    AbsLog(tagPrefix = tagPrefix, separator = "-", logLevel = logLevel, enableLog = enableLog) {

    override fun printVerbLog(tag: String, message: String, outputType: LogOutType) {
        Log.v(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }

    override fun printDebugLog(tag: String, message: String, outputType: LogOutType) {
        Log.d(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }

    override fun printInfoLog(tag: String, message: String, outputType: LogOutType) {
        Log.i(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }

    override fun printWarnLog(tag: String, message: String, outputType: LogOutType) {
        Log.w(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }

    override fun printErrorLog(tag: String, message: String, outputType: LogOutType) {
        Log.e(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }

    override fun printFatalLog(tag: String, message: String, outputType: LogOutType) {
        Log.wtf(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }
}
