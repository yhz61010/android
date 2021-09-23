package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import com.leovp.androidbase.utils.file.FileDocumentUtil
import com.leovp.androidbase.utils.log.LogContext
import java.io.File
import kotlin.system.exitProcess

/**
 * Author: Michael Leo
 * Date: 20-11-30 下午2:54
 */

// TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, ctx.resources.displayMetrics).toInt()
fun dp2px(dipValue: Float) = (dipValue * app.resources.displayMetrics.density + 0.5f).toInt()

fun px2dp(pxValue: Float) = (pxValue / app.resources.displayMetrics.density + 0.5f).toInt()

fun sp2px(spValue: Float, ctx: Context? = null): Int {
    return if (ctx == null) {
        val fontScale: Float = Resources.getSystem().displayMetrics.scaledDensity
        (spValue * fontScale + 0.5f).toInt()
    } else {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spValue, ctx.resources.displayMetrics).toInt()
    }
}

/**
 * Converts an unpacked complex data value holding a dimension to its final floating point value.
 *
 * @param unit [TypedValue]
 * TypedValue.COMPLEX_UNIT_DIP: dp -> px
 * TypedValue.COMPLEX_UNIT_PT:  pt -> px
 * TypedValue.COMPLEX_UNIT_MM:  mm -> px
 * TypedValue.COMPLEX_UNIT_IN:  inch -> px
 */
@JvmOverloads
fun px(value: Float, unit: Int = TypedValue.COMPLEX_UNIT_DIP) = TypedValue.applyDimension(unit, value, app.resources.displayMetrics).toInt()


/**
 * Get meta data in Activity or Application.<br></br>
 * Notice that, if you want to get meta data in Service or Broadcast using [.getMetaData] instead.
 *
 * **Attention:** It's not working at all.
 *
 * @param ctx The context of Activity or Application
 * @param key The meta data key
 * @return The value of meta data
 */
@Suppress("unused")
fun getMetaData(ctx: Context, key: String): String? {
    return getMetaData<Any>(ctx, key, null)
}

/**
 * Get meta data in all scope including Activity, Application, Service and Broadcast.<br></br>
 *
 * **Attention:** It's not working at all.
 * @param ctx   The context
 * @param key   The meta data key
 * @param clazz The class of Service and Broadcast. For Activity or Application, set `null` to this parameter.
 * @return The value of meta data
 */
@Suppress("unused")
fun <T> getMetaData(ctx: Context, key: String, clazz: Class<T>?): String? {
    var metaData: String? = ""
    try {
        if (ctx is Activity) {
            val info = ctx.getPackageManager().getActivityInfo(ctx.componentName, PackageManager.GET_META_DATA)
            metaData = info.metaData.getString(key)
            return metaData
        }
        if (ctx is Application) {
            val info =
                ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA)
            metaData = info.metaData.getString(key)
            return metaData
        }
        if (clazz != null && ctx is Service) {
            val cn = ComponentName(ctx, clazz)
            val info = ctx.getPackageManager().getServiceInfo(cn, PackageManager.GET_META_DATA)
            metaData = info.metaData.getString(key)
            return metaData
        }

        // BroadcastReceiver
        if (clazz != null && "android.content.BroadcastReceiver" == clazz.simpleName) {
            val cn = ComponentName(ctx, clazz)
            val info = ctx.packageManager.getReceiverInfo(cn, PackageManager.GET_META_DATA)
            metaData = info.metaData.getString(key)
            return metaData
        }
    } catch (e: Exception) {
        metaData = ""
        return metaData
    }
    return metaData
}

// https://stackoverflow.com/questions/4604239/install-application-programmatically-on-android
fun installApk(file: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val downloadedApkUri = FileDocumentUtil.getFileUri(app, file)
            intent.setDataAndType(downloadedApkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
fun exitApp() {
    try {
        for (appTask in app.activityManager.appTasks) {
            appTask.finishAndRemoveTask()
        }
        exitProcess(0)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun startApp(targetPackageName: String = app.id) {
    app.packageManager.getLaunchIntentForPackage(targetPackageName)?.let {
        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        app.startActivity(it)
    }
}

fun restartApp(targetIntent: Intent) {
    targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    app.startActivity(targetIntent)
    Process.killProcess(Process.myPid())
//        Runtime.getRuntime().exit(0)
}

/**
 * Remove Android P warning dialog.
 *
 * Detected problems with API compatibility(visit g.co/dev/appcompat for more info)
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun closeAndroidPDialog() {
    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
        LogContext.log.w("AppExt", "Not Android 9. Do not closeAndroidPDialog")
        return
    }
    LogContext.log.w("AppExt", "closeAndroidPDialog on Android 9")
    try {
        val aClass = Class.forName("android.content.pm.PackageParser\$Package")
        val declaredConstructor = aClass.getDeclaredConstructor(String::class.java)
        declaredConstructor.isAccessible = true
    } catch (e: Exception) {
        e.printStackTrace()
    }
    try {
        val cls = Class.forName("android.app.ActivityThread")
        val declaredMethod = cls.getDeclaredMethod("currentActivityThread")
        declaredMethod.isAccessible = true
        val activityThread = declaredMethod.invoke(null)
        val mHiddenApiWarningShown = cls.getDeclaredField("mHiddenApiWarningShown")
        mHiddenApiWarningShown.isAccessible = true
        mHiddenApiWarningShown.setBoolean(activityThread, true)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ============================================================================

fun openSoftKeyboard() {
    val imm = app.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
}

fun Activity.closeSoftKeyboard() {
    val view = this.window.peekDecorView()
    if (view != null) {
        val imm = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}