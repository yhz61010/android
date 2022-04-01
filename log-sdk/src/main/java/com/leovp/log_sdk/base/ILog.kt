package com.leovp.log_sdk.base

import android.util.Log

/**
 * Inherit this class and just implement `printXXXLog` methods.
 *
 * The `outputType` parameter in `printXXXLog` methods is just the handy parameter for you to identify the log output type.
 * So that you can control how to output the log. For example, just output some logs on console or send some logs to server.
 *
 * Author: Michael Leo
 * Date: 2020/10/16 下午5:33
 */
interface ILog {
    var enableLog: Boolean

    fun getTagName(tag: String): String
    fun getLogMaxLength(): Int = MAX_LENGTH

    fun printVerbLog(tag: String, message: String, outputType: Int)
    fun printDebugLog(tag: String, message: String, outputType: Int)
    fun printInfoLog(tag: String, message: String, outputType: Int)
    fun printWarnLog(tag: String, message: String, outputType: Int)
    fun printErrorLog(tag: String, message: String, outputType: Int)
    fun printFatalLog(tag: String, message: String, outputType: Int)

    // ==================================================

    fun v(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) = v(ITAG, message, throwable, fullOutput, outputType)
    fun v(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.VERB, getTagName(tag), getMessage(message, throwable), outputType)
            else printVerbLog(getTagName(tag), getMessage(message, throwable), outputType)
        }
    }

    fun d(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) = d(ITAG, message, throwable, fullOutput, outputType)
    fun d(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.DEBUG, getTagName(tag), getMessage(message, throwable), outputType)
            else printDebugLog(getTagName(tag), getMessage(message, throwable), outputType)
        }
    }

    fun i(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) = i(ITAG, message, throwable, fullOutput, outputType)
    fun i(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.INFO, getTagName(tag), getMessage(message, throwable), outputType)
            else printInfoLog(getTagName(tag), getMessage(message, throwable), outputType)
        }
    }

    fun w(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) = w(ITAG, message, throwable, fullOutput, outputType)
    fun w(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.WARN, getTagName(tag), getMessage(message, throwable), outputType)
            else printWarnLog(getTagName(tag), getMessage(message, throwable), outputType)
        }
    }

    fun e(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) = e(ITAG, message, throwable, fullOutput, outputType)
    fun e(tag: String, message: String? = null, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.ERROR, getTagName(tag), getMessage(message, throwable), outputType)
            else printErrorLog(getTagName(tag), getMessage(message, throwable), outputType)
        }
    }

    fun f(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) = f(ITAG, message, throwable, fullOutput, outputType)
    fun f(tag: String, message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: Int = -1) {
        if (enableLog) {
            if (fullOutput) splitOutputMessage(LogLevel.FATAL, getTagName(tag), getMessage(message, throwable), outputType)
            else printFatalLog(getTagName(tag), getMessage(message, throwable), outputType)
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

    private fun splitOutputMessage(logLevel: LogLevel, tag: String, message: String, outputType: Int) {
        if (message.length > getLogMaxLength()) {
            outputLog(logLevel, tag, message.substring(0, getLogMaxLength()), outputType)
            splitOutputMessage(logLevel, tag, message.substring(getLogMaxLength()), outputType)
        } else {
            outputLog(logLevel, tag, message, outputType)
        }
    }

    private fun outputLog(logLevel: LogLevel, tag: String, message: String, outputType: Int) {
        when (logLevel) {
            LogLevel.VERB -> printVerbLog(tag, message, outputType)
            LogLevel.DEBUG -> printDebugLog(tag, message, outputType)
            LogLevel.INFO -> printInfoLog(tag, message, outputType)
            LogLevel.WARN -> printWarnLog(tag, message, outputType)
            LogLevel.ERROR -> printErrorLog(tag, message, outputType)
            LogLevel.FATAL -> printFatalLog(tag, message, outputType)
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
        private const val MAX_LENGTH = 2000
        const val OUTPUT_TYPE_SYSTEM = 0x20211009
        const val OUTPUT_TYPE_CLIENT_COMMAND = OUTPUT_TYPE_SYSTEM + 1
        const val OUTPUT_TYPE_HTTP_HEADER_COOKIE = OUTPUT_TYPE_SYSTEM + 2
    }
}