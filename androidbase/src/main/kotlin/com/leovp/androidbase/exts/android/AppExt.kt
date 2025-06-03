@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.leovp.android.exts.inputMethodManager
import com.leovp.android.utils.FileDocumentUtil
import com.leovp.log.LogContext
import java.io.File
import kotlin.system.exitProcess

/**
 * Author: Michael Leo
 * Date: 20-11-30 下午2:54
 */

private const val TAG = "AppExt"

// https://stackoverflow.com/questions/4604239/install-application-programmatically-on-android
fun Context.installApk(file: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val downloadedApkUri = FileDocumentUtil.getFileUri(this, file)
        intent.setDataAndType(downloadedApkUri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // } else {
        //     intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        //     intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        // }
        startActivity(intent)
    } catch (e: Exception) {
        LogContext.log.e(TAG, "installApk exception.")
    }
}

fun ActivityManager.exitApp() {
    try {
        for (appTask in this.appTasks) {
            appTask.finishAndRemoveTask()
        }
        exitProcess(0)
    } catch (e: Exception) {
        LogContext.log.e(TAG, "exitApp exception.")
    }
}

fun Context.startApp(targetPackageName: String) {
    this.packageManager.getLaunchIntentForPackage(targetPackageName)?.let {
        it.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        this.startActivity(it)
    }
}

fun Context.restartApp(targetIntent: Intent) {
    targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    this.startActivity(targetIntent)
    Process.killProcess(Process.myPid())
    //        Runtime.getRuntime().exit(0)
}

/**
 * Remove Android P warning dialog.
 *
 * Detected problems with API compatibility(visit g.co/dev/appcompat for more info)
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
fun closeAndroidPDialog() {
    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
        LogContext.log.w(TAG, "Not Android 9. Do not closeAndroidPDialog")
        return
    }
    LogContext.log.w(TAG, "closeAndroidPDialog on Android 9")
    try {
        val aClass = Class.forName("android.content.pm.PackageParser\$Package")
        val declaredConstructor = aClass.getDeclaredConstructor(String::class.java)
        declaredConstructor.isAccessible = true
    } catch (e: Exception) {
        LogContext.log.e(TAG, "1 closeAndroidPDialog exception.")
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
        LogContext.log.e(TAG, "2 closeAndroidPDialog exception.")
    }
}

// ============================================================================

fun Activity.openSoftKeyboard() {
    //    val imm = app.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    //    imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
    inputMethodManager.showSoftInput(window.decorView, 0)
}

fun Activity.closeSoftKeyboard() {
    val view = this.window.peekDecorView()
    if (view != null) {
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
