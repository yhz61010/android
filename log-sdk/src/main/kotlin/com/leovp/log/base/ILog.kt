package com.leovp.log.base

/**
 * Author: Michael Leo
 * Date: 2020/10/16 下午5:33
 */
internal interface ILog {
    fun printVerbLog(tag: String, message: String, outputType: Int)
    fun printDebugLog(tag: String, message: String, outputType: Int)
    fun printInfoLog(tag: String, message: String, outputType: Int)
    fun printWarnLog(tag: String, message: String, outputType: Int)
    fun printErrorLog(tag: String, message: String, outputType: Int)
    fun printFatalLog(tag: String, message: String, outputType: Int)
}
