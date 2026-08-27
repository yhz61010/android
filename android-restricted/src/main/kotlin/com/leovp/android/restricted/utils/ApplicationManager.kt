package com.leovp.android.restricted.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2022/10/17 09:27
 */
object ApplicationManager {
    private const val TAG = "ApplicationManager"

    @Volatile
    private var app: Application? = null

    val application: Application
        get() = app ?: synchronized(this) { app ?: getApplicationByReflect().also { app = it } }

    /**
     * This method is NOT mandatory.
     * You can call [application] directly to get application by reflection.
     *
     * Reason: When you reassign app permission,
     * the app may be re-run. However, the custom application will be run again.
     * In addition, all the property values in singleton will also be lost.
     * In that case, I need to get application by reflection.
     */
    fun init(context: Context? = null) {
        if (app == null) {
            synchronized(this) {
                if (app == null) {
                    app = (context?.applicationContext as? Application) ?: getApplicationByReflect()
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun getApplicationByReflect(): Application = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val currentThread = activityThread.getMethod("currentActivityThread").invoke(null)
        val application =
            activityThread.getMethod("getApplication").invoke(currentThread) as? Application
        requireNotNull(application) {
            "ActivityThread.getApplication() returned null"
        }
    }.getOrElse {
        LogContext.log.e(TAG, "Application reflection failed; call init(context) first", it)
        throw IllegalStateException(
            "ApplicationManager is not initialized. Call init(context) first.",
            it
        )
    }
}
