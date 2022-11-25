package com.leovp.android.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

/**
 * Author: Michael Leo
 * Date: 2022/10/17 09:27
 */
object ApplicationManager {
    private var app: Application? = null

    val application: Application = app ?: getApplicationByReflect()

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
            app = (context?.applicationContext as? Application) ?: getApplicationByReflect()
        }
    }

    private fun getApplicationByReflect(): Application {
        @SuppressLint("PrivateApi")
        val activityThread = Class.forName("android.app.ActivityThread")
        val at = activityThread.getMethod("currentActivityThread").invoke(null)
        val app = activityThread.getMethod("getApplication").invoke(at) ?: error("u should init first")
        init(app as Application)
        return app
    }
}
