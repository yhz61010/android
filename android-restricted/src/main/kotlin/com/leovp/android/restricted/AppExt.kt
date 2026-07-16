@file:Suppress("unused")

package com.leovp.android.restricted

import android.annotation.SuppressLint
import android.os.Build
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2026/7/16 14:20
 */

private const val TAG = "AppExt"

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
        LogContext.log.e(TAG, "1 closeAndroidPDialog exception.", e)
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
        LogContext.log.e(TAG, "2 closeAndroidPDialog exception.", e)
    }
}
