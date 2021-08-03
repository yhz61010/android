package com.leovp.androidbase.utils.log.base

import android.util.Log
import com.leovp.androidbase.exts.kotlin.ITAG

/**
 * Author: Michael Leo
 * Date: 2020/10/16 下午5:33
 */
interface ILog {
    var enableLog: Boolean

    fun getTagName(tag: String): String

    fun printVerbLog(tag: String, message: String)
    fun printDebugLog(tag: String, message: String)
    fun printInfoLog(tag: String, message: String)
    fun printWarnLog(tag: String, message: String)
    fun printErrorLog(tag: String, message: String)
    fun printFatalLog(tag: String, message: String)

    // ==================================================

    fun v(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) = v(ITAG, message, throwable, fullOutput)
    fun v(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.VERB, getTagName(tag), getMessage(message, throwable))
            else printVerbLog(getTagName(tag), getMessage(message, throwable))
        }
    }

    fun d(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) = d(ITAG, message, throwable, fullOutput)
    fun d(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.DEBUG, getTagName(tag), getMessage(message, throwable))
            else printDebugLog(getTagName(tag), getMessage(message, throwable))
        }
    }

    fun i(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) = i(ITAG, message, throwable, fullOutput)
    fun i(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.INFO, getTagName(tag), getMessage(message, throwable))
            else printInfoLog(getTagName(tag), getMessage(message, throwable))
        }
    }

    fun w(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) = w(ITAG, message, throwable, fullOutput)
    fun w(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.WARN, getTagName(tag), getMessage(message, throwable))
            else printWarnLog(getTagName(tag), getMessage(message, throwable))
        }
    }

    fun e(message: String?, fullOutput: Boolean = false) = e(ITAG, message, null, fullOutput)
    fun e(throwable: Throwable?, fullOutput: Boolean = false) = e(ITAG, "", throwable, fullOutput)
    fun e(tag: String, throwable: Throwable?, fullOutput: Boolean = false) = e(tag, "", throwable, fullOutput)
    fun e(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.ERROR, getTagName(tag), getMessage(message, throwable))
            else printErrorLog(getTagName(tag), getMessage(message, throwable))
        }
    }

    fun f(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) = f(ITAG, message, throwable, fullOutput)
    fun f(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.FATAL, getTagName(tag), getMessage(message, throwable))
            else printFatalLog(getTagName(tag), getMessage(message, throwable))
        }
    }

    // ==================================================

    @Suppress("unused")
    fun getStackTraceString(t: Throwable?): String {
        return Log.getStackTraceString(t)
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

    private fun splitOutputMessage(logLevel: LogLevel, tag: String, message: String) {
        if (message.length > MAX_LENGTH) {
            outputLog(logLevel, tag, message.substring(0, MAX_LENGTH))
            splitOutputMessage(logLevel, tag, message.substring(MAX_LENGTH))
        } else {
            outputLog(logLevel, tag, message)
        }
    }

    private fun outputLog(logLevel: LogLevel, tag: String, message: String) {
        when (logLevel) {
            LogLevel.VERB -> printVerbLog(tag, message)
            LogLevel.DEBUG -> printDebugLog(tag, message)
            LogLevel.INFO -> printInfoLog(tag, message)
            LogLevel.WARN -> printWarnLog(tag, message)
            LogLevel.ERROR -> printErrorLog(tag, message)
            LogLevel.FATAL -> printFatalLog(tag, message)
        }
    }

    enum class LogLevel {
        VERB,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL
    }

    companion object {
        private const val MAX_LENGTH = 4000
    }
}