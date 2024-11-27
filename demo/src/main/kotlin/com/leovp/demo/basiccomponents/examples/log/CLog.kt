package com.leovp.demo.basiccomponents.examples.log

import android.content.Context
import com.leovp.log.base.AbsLog
import com.leovp.log.base.LogLevel
import com.leovp.log.base.LogOutType
import com.tencent.mars.xlog.Log
import com.tencent.mars.xlog.Xlog
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-4-20 上午11:39
 */
@Suppress("unused")
class CLog(
    tagPrefix: String,
    logLevel: LogLevel = LogLevel.VERB,
    enableLog: Boolean = true,
) : AbsLog(tagPrefix = tagPrefix, separator = "-", logLevel = logLevel, enableLog = enableLog) {

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
        Log.f(tag, if (outputType == LogOutType.COMMON) message else "[$outputType]$message")
    }

    // ==============================

    private fun getLogDir(ctx: Context, baseFolderName: String): File {
        val builder = getBaseDirString(ctx, baseFolderName) + File.separator + "log"
        val dir = File(builder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Suppress("WeakerAccess")
    private fun getBaseDirString(ctx: Context, baseFolderName: String): String {
        return ctx.getExternalFilesDir(null)?.let { it.absolutePath + File.separator + baseFolderName } ?: ""
    }

    fun init(context: Context) {
        System.loadLibrary("c++_shared")
        System.loadLibrary("marsxlog")

        val logDir = getLogDir(context, "xlog").absolutePath
        val cacheDir = getLogDir(context, "x-cache-dir").absolutePath
        val defaultLogLevel = when (logLevel) {
            LogLevel.VERB -> Xlog.LEVEL_VERBOSE
            LogLevel.DEBUG -> Xlog.LEVEL_DEBUG
            LogLevel.INFO -> Xlog.LEVEL_INFO
            LogLevel.WARN -> Xlog.LEVEL_WARNING
            LogLevel.ERROR -> Xlog.LEVEL_ERROR
            LogLevel.FATAL -> Xlog.LEVEL_FATAL
        }

        Log.setLogImp(Xlog())
        Log.setConsoleLogOpen(true)
        Log.appenderOpen(defaultLogLevel, Xlog.AppednerModeAsync, cacheDir, logDir, "main", 0)

        //        // Now, there is no way to use this XLogConfig. Probably this is a Xlog bug.
        //        val logConfig = Xlog.XLogConfig()
        //        logConfig.mode = Xlog.AppednerModeAsync
        //        logConfig.logdir = logDir.absolutePath
        //        logConfig.nameprefix = "main"
        //        logConfig.pubkey = if (debugMode) "" else context.packageName
        //        logConfig.compressmode = Xlog.ZLIB_MODE
        //        logConfig.compresslevel = 0
        //        logConfig.cachedir = cacheDir.absolutePath
        //        logConfig.cachedays = 5
        //        if (debugMode) {
        //            logConfig.level = Xlog.LEVEL_VERBOSE
        //        } else {
        //            logConfig.level = Xlog.LEVEL_INFO
        //        }
        //        Log.setLogImp(Xlog().apply { setConsoleLogOpen(0, debugMode) })
    }

    fun flushLog(isSync: Boolean = false) {
        Log.appenderFlushSync(isSync)
    }

    fun closeLog() {
        Log.appenderClose()
    }
}
