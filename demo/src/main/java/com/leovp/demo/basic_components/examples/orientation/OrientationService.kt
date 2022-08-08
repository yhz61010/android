package com.leovp.demo.basic_components.examples.orientation

import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.view.IRotationWatcher
import com.leovp.androidbase.framework.BaseService
import com.leovp.lib_common_android.exts.surfaceRotationName
import com.leovp.lib_reflection.wrappers.ServiceManager
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class OrientationService : BaseService() {

    private val screenRotationChanged: IRotationWatcher.Stub = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            LogContext.log.w(ITAG, "Device rotation changed to ${rotation.surfaceRotationName}")
        }
    }

    private var lastOrientation = -1

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation != lastOrientation) {
            // Checks the orientation of the screen
            when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    // toast("Device is in Landscape mode.", debug = true)
//                    LogContext.log.w(ITAG, "Device is in Landscape mode.")
                }
                Configuration.ORIENTATION_PORTRAIT  -> {
                    // toast("Device is in Portrait mode.", debug = true)
//                    LogContext.log.w(ITAG, "Device is in Portrait mode.")
                }
            }
            lastOrientation = newConfig.orientation
        }
    }

    override fun onCreate() {
        LogContext.log.i(ITAG, "=====> onCreate <=====")
        super.onCreate()
        ServiceManager.windowManager?.registerRotationWatcher(screenRotationChanged)
    }

    override fun onDestroy() {
        LogContext.log.i(ITAG, "=====> onDestroy <=====")
        ServiceManager.windowManager?.removeRotationWatcher(screenRotationChanged)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}