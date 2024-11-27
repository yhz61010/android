package com.leovp.log.base

/**
 * Author: Michael Leo
 * Date: 2020/10/16 下午5:33
 */
internal interface ILog {
    fun printVerbLog(tag: String, message: String, outputType: LogOutType)
    fun printDebugLog(tag: String, message: String, outputType: LogOutType)
    fun printInfoLog(tag: String, message: String, outputType: LogOutType)
    fun printWarnLog(tag: String, message: String, outputType: LogOutType)
    fun printErrorLog(tag: String, message: String, outputType: LogOutType)
    fun printFatalLog(tag: String, message: String, outputType: LogOutType)
}

enum class LogLevel {
    VERB,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
}

@Suppress("unused")
@JvmInline
value class LogOutType(val type: Int) {
    companion object {
        private const val OUTPUT_TYPE_SYSTEM = 0x20211009
        private const val OUTPUT_TYPE_CLIENT_COMMAND = OUTPUT_TYPE_SYSTEM + 1
        private const val OUTPUT_TYPE_HTTP_HEADER = OUTPUT_TYPE_SYSTEM + 2
        private const val OUTPUT_TYPE_FRAMEWORK = OUTPUT_TYPE_SYSTEM + 3
        private const val OUTPUT_TYPE_HTTP = OUTPUT_TYPE_SYSTEM + 4
        private const val OUTPUT_TYPE_COMMON = OUTPUT_TYPE_SYSTEM + 5

        val SYSTEM = LogOutType(OUTPUT_TYPE_SYSTEM)
        val CLIENT_COMMAND = LogOutType(OUTPUT_TYPE_CLIENT_COMMAND)
        val HTTP_HEADER = LogOutType(OUTPUT_TYPE_HTTP_HEADER)
        val FRAMEWORK = LogOutType(OUTPUT_TYPE_FRAMEWORK)
        val HTTP = LogOutType(OUTPUT_TYPE_HTTP)
        val COMMON = LogOutType(OUTPUT_TYPE_COMMON)
    }

    override fun toString(): String = when (this) {
        SYSTEM -> "SYSTEM"
        CLIENT_COMMAND -> "CLIENT_COMMAND"
        HTTP_HEADER -> "HTTP_HEADER"
        FRAMEWORK -> "FRAMEWORK"
        HTTP -> "HTTP"
        COMMON -> "COMMON"
        else -> this.toString()
    }
}
