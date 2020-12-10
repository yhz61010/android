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

    override fun v(tag: String, message: String?) {
        if (enableLog) Log.v(getTagName(tag), message ?: "[null]")
    }

    override fun d(tag: String, message: String?) {
        if (enableLog) Log.d(getTagName(tag), message ?: "[null]")
    }

    override fun i(tag: String, message: String?) {
        if (enableLog) Log.i(getTagName(tag), message ?: "[null]")
    }

    override fun w(tag: String, message: String?) {
        if (enableLog) Log.w(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, message: String?) {
        if (enableLog) Log.e(getTagName(tag), message ?: "[null]")
    }

    override fun e(tag: String, throwable: Throwable?) {
        if (enableLog) e(tag, null, throwable)
    }

    override fun f(tag: String, message: String?) {
        if (enableLog) Log.f(getTagName(tag), message ?: "[null]")
    }

    override fun v(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.v(getTagName(tag), getMessage(message, throwable))
    }

    override fun d(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.d(getTagName(tag), getMessage(message, throwable))
    }

    override fun i(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.i(getTagName(tag), getMessage(message, throwable))
    }

    override fun w(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.w(getTagName(tag), getMessage(message, throwable))
    }

    override fun e(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.e(getTagName(tag), getMessage(message, throwable))
    }

    override fun f(tag: String, message: String?, throwable: Throwable?) {
        if (enableLog) Log.f(getTagName(tag), getMessage(message, throwable))
    }

    fun flushLog(isSync: Boolean = true) {
        Log.appenderFlush(isSync)
    }

    fun closeLog() {
        Log.appenderClose()
    }
}