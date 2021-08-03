package com.leovp.leoandroidbaseutil.basic_components.examples.log

import android.content.Context
import com.leovp.androidbase.utils.log.base.ILog
import com.leovp.leoandroidbaseutil.BuildConfig
import com.tencent.mars.xlog.Log
import com.tencent.mars.xlog.Xlog
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-4-20 上午11:39
 */
@Suppress("unused")
class CLog : ILog {
    private val debugMode = BuildConfig.DEBUG

    override fun getTagName(tag: String) = "LEO-$tag"

    override var enableLog = true

    override fun printVerbLog(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun printDebugLog(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun printInfoLog(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun printWarnLog(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun printErrorLog(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun printFatalLog(tag: String, message: String) {
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

        val logDir = getLogDir(context, "xlog")
        val cacheDir = getLogDir(context, "x-cache-dir")

        // this is necessary, or may crash for SIGBUS
//        val cachePath = "${context.filesDir}/xlog"

        //init xlog
        val logConfig = Xlog.XLogConfig()
        logConfig.mode = Xlog.AppednerModeAsync
        logConfig.logdir = logDir.absolutePath
        logConfig.nameprefix = "main"
        logConfig.pubkey = if (debugMode) "x" else context.packageName
        logConfig.compressmode = Xlog.ZLIB_MODE
        logConfig.compresslevel = 0
        logConfig.cachedir = cacheDir.absolutePath
        logConfig.cachedays = 5
        if (debugMode) {
            logConfig.level = Xlog.LEVEL_VERBOSE
            Xlog.setConsoleLogOpen(true)
        } else {
            logConfig.level = Xlog.LEVEL_INFO
            Xlog.setConsoleLogOpen(false)
        }
        Log.setLogImp(Xlog())
    }

    fun flushLog(isSync: Boolean = false) {
        Log.appenderFlush(isSync)
    }

    fun closeLog() {
        Log.appenderClose()
    }
}