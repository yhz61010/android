package com.leovp.demo.basic_components.examples.log

import android.content.Context
import com.leovp.demo.BuildConfig
import com.tencent.mars.xlog.Log
import com.tencent.mars.xlog.Xlog
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-4-20 上午11:39
 */
@Suppress("unused")
class CLog : com.leovp.log_sdk.base.ILog {
    private val debugMode = BuildConfig.DEBUG

    override fun getTagName(tag: String) = "LEO-$tag"

    override var enableLog = true

    override fun printVerbLog(tag: String, message: String, outputType: Int) {
        Log.v(tag, message)
    }

    override fun printDebugLog(tag: String, message: String, outputType: Int) {
        Log.d(tag, message)
    }

    override fun printInfoLog(tag: String, message: String, outputType: Int) {
        Log.i(tag, message)
    }

    override fun printWarnLog(tag: String, message: String, outputType: Int) {
        Log.w(tag, message)
    }

    override fun printErrorLog(tag: String, message: String, outputType: Int) {
        Log.e(tag, message)
    }

    override fun printFatalLog(tag: String, message: String, outputType: Int) {
        Log.f(tag, message)
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
        val defaultLogLevel = if (debugMode) Xlog.LEVEL_VERBOSE else Xlog.LEVEL_INFO

        Log.setLogImp(Xlog())
        Log.setConsoleLogOpen(debugMode)
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
