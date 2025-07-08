package com.leovp.log.base

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
abstract class AbsLog(
    private val tagPrefix: String,
    private val separator: String = "-",
    val logLevel: LogLevel = LogLevel.VERB,
    val enableLog: Boolean = true,
) : ILog {

    private fun getTagName(tag: String): String = "$tagPrefix${separator}$tag".take(20)

    // @Deprecated(
    //     message = "Use the function with the 'tag' parameter.",
    //     replaceWith = ReplaceWith("v(TAG, message, throwable, fullOutput, outputType)"),
    // )
    // fun v(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: LogOutType = LogOutType.COMMON,) =
    //     v(ITAG, message, throwable, fullOutput, outputType)

    fun v(
        tag: String,
        message: String?,
        throwable: Throwable? = null,
        fullOutput: Boolean = false,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (LogLevel.VERB >= logLevel && enableLog) {
            if (fullOutput) {
                splitOutputMessage(LogLevel.VERB, getTagName(tag), getMessage(message, throwable), outputType)
            } else {
                printVerbLog(getTagName(tag), getMessage(message, throwable), outputType)
            }
        }
    }

    // @Deprecated(
    //     message = "Use the function with the 'tag' parameter.",
    //     replaceWith = ReplaceWith("d(TAG, message, throwable, fullOutput, outputType)"),
    // )
    // fun d(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: LogOutType = LogOutType.COMMON,) =
    //     d(ITAG, message, throwable, fullOutput, outputType)

    fun d(
        tag: String,
        message: String?,
        throwable: Throwable? = null,
        fullOutput: Boolean = false,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (LogLevel.DEBUG >= logLevel && enableLog) {
            if (fullOutput) {
                splitOutputMessage(LogLevel.DEBUG, getTagName(tag), getMessage(message, throwable), outputType)
            } else {
                printDebugLog(getTagName(tag), getMessage(message, throwable), outputType)
            }
        }
    }

    // @Deprecated(
    //     message = "Use the function with the 'tag' parameter.",
    //     replaceWith = ReplaceWith("i(TAG, message, throwable, fullOutput, outputType)"),
    // )
    // fun i(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: LogOutType = LogOutType.COMMON,) =
    //     i(ITAG, message, throwable, fullOutput, outputType)

    fun i(
        tag: String,
        message: String?,
        throwable: Throwable? = null,
        fullOutput: Boolean = false,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (LogLevel.INFO >= logLevel && enableLog) {
            if (fullOutput) {
                splitOutputMessage(LogLevel.INFO, getTagName(tag), getMessage(message, throwable), outputType)
            } else {
                printInfoLog(getTagName(tag), getMessage(message, throwable), outputType)
            }
        }
    }

    // @Deprecated(
    //     message = "Use the function with the 'tag' parameter.",
    //     replaceWith = ReplaceWith("w(TAG, message, throwable, fullOutput, outputType)"),
    // )
    // fun w(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: LogOutType = LogOutType.COMMON,) =
    //     w(ITAG, message, throwable, fullOutput, outputType)

    fun w(
        tag: String,
        message: String?,
        throwable: Throwable? = null,
        fullOutput: Boolean = false,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (LogLevel.WARN >= logLevel && enableLog) {
            if (fullOutput) {
                splitOutputMessage(LogLevel.WARN, getTagName(tag), getMessage(message, throwable), outputType)
            } else {
                printWarnLog(getTagName(tag), getMessage(message, throwable), outputType)
            }
        }
    }

    // @Deprecated(
    //     message = "Use the function with the 'tag' parameter.",
    //     replaceWith = ReplaceWith("e(TAG, message, throwable, fullOutput, outputType)"),
    // )
    // fun e(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: LogOutType = LogOutType.COMMON,) =
    //     e(ITAG, message, throwable, fullOutput, outputType)

    fun e(
        tag: String,
        message: String?,
        throwable: Throwable? = null,
        fullOutput: Boolean = false,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (LogLevel.ERROR >= logLevel && enableLog) {
            if (fullOutput) {
                splitOutputMessage(LogLevel.ERROR, getTagName(tag), getMessage(message, throwable), outputType)
            } else {
                printErrorLog(getTagName(tag), getMessage(message, throwable), outputType)
            }
        }
    }

    // @Deprecated(
    //     message = "Use the function with the 'tag' parameter.",
    //     replaceWith = ReplaceWith("f(TAG, message, throwable, fullOutput, outputType)"),
    // )
    // fun f(message: String?, throwable: Throwable? = null, fullOutput: Boolean = false, outputType: LogOutType = LogOutType.COMMON,) =
    //     f(ITAG, message, throwable, fullOutput, outputType)

    fun f(
        tag: String,
        message: String?,
        throwable: Throwable? = null,
        fullOutput: Boolean = false,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (LogLevel.FATAL >= logLevel && enableLog) {
            if (fullOutput) {
                splitOutputMessage(LogLevel.FATAL, getTagName(tag), getMessage(message, throwable), outputType)
            } else {
                printFatalLog(getTagName(tag), getMessage(message, throwable), outputType)
            }
        }
    }

    // ==================================================

    @Suppress("MemberVisibilityCanBePrivate")
    fun getStackTraceString(t: Throwable?): String {
        return Log.getStackTraceString(t)
    }

    // Usage: getStackTraceString(Thread.currentThread().getStackTrace())
    @Suppress("unused")
    fun getStackTraceString(elements: Array<StackTraceElement>?): String {
        if (elements.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        elements.forEach { sb.append('\n').append(it.toString()) }
        return sb.toString()
    }

    @Suppress("MemberVisibilityCanBePrivate")
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

    private fun splitOutputMessage(
        logLevel: LogLevel,
        tag: String,
        message: String,
        outputType: LogOutType = LogOutType.COMMON,
    ) {
        if (message.length > MAX_LENGTH) {
            outputLog(logLevel, tag, message.substring(0, MAX_LENGTH), outputType)
            splitOutputMessage(logLevel, tag, message.substring(MAX_LENGTH), outputType)
        } else {
            outputLog(logLevel, tag, message, outputType)
        }
    }

    private fun outputLog(logLevel: LogLevel, tag: String, message: String, outputType: LogOutType) {
        when (logLevel) {
            LogLevel.VERB -> printVerbLog(tag, message, outputType)
            LogLevel.DEBUG -> printDebugLog(tag, message, outputType)
            LogLevel.INFO -> printInfoLog(tag, message, outputType)
            LogLevel.WARN -> printWarnLog(tag, message, outputType)
            LogLevel.ERROR -> printErrorLog(tag, message, outputType)
            LogLevel.FATAL -> printFatalLog(tag, message, outputType)
        }
    }

    companion object {
        private const val MAX_LENGTH = 2000
    }
}
