package com.leovp.demo.basiccomponents.examples.orientation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.view.IRotationWatcher
import android.view.OrientationEventListener
import com.leovp.android.exts.screenSurfaceRotation
import com.leovp.android.exts.surfaceRotationName
import com.leovp.androidbase.framework.BaseService
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.reflection.wrappers.ServiceManager

class OrientationService : BaseService() {

    private val screenRotationChanged: IRotationWatcher.Stub = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            LogContext.log.w(ITAG, "Reflection: Device rotation changed to ${rotation.surfaceRotationName}")
        }
    }

    private var lastOrientation = -1

    @SuppressLint("SwitchIntDef")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation != lastOrientation) {
            // Checks the orientation of the screen
            when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    // toast("Device is in Landscape mode.", debug = true)
                    LogContext.log.w(ITAG, "Device is in Landscape mode.")
                }
                Configuration.ORIENTATION_PORTRAIT -> {
                    // toast("Device is in Portrait mode.", debug = true)
                    LogContext.log.w(ITAG, "Device is in Portrait mode.")
                }
            }
            lastOrientation = newConfig.orientation
        }
    }

    private var deviceOrientationEventListener: ServiceOrientationListener? = null

    override fun onCreate() {
        LogContext.log.i(ITAG, "=====> onCreate <=====")
        super.onCreate()
        deviceOrientationEventListener = ServiceOrientationListener(this)
        deviceOrientationEventListener?.enable()
        ServiceManager.windowManager?.registerRotationWatcher(screenRotationChanged)
    }

    override fun onDestroy() {
        LogContext.log.i(ITAG, "=====> onDestroy <=====")
        deviceOrientationEventListener?.disable()
        ServiceManager.windowManager?.removeRotationWatcher(screenRotationChanged)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private var lastScreenSurfaceRotation = -1

    inner class ServiceOrientationListener(ctx: Context) : OrientationEventListener(ctx) {
        @SuppressLint("SetTextI18n")
        override fun onOrientationChanged(degree: Int) {
            if (degree == ORIENTATION_UNKNOWN) {
                LogContext.log.w("ORIENTATION_UNKNOWN")
                return
            }

            // Use parameter degree to determine the device orientation.
            LogContext.log.i(ITAG, "=====> In Service: rotation=$degree")

            val ssr = screenSurfaceRotation
            if (lastScreenSurfaceRotation != ssr) {
                LogContext.log.w(ITAG, "=====> Current screen rotation=${ssr.surfaceRotationName}")
            }
            lastScreenSurfaceRotation = ssr
        }
    }
}
