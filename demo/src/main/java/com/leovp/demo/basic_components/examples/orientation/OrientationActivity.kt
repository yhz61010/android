package com.leovp.demo.basic_components.examples.orientation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.IRotationWatcher
import android.view.OrientationEventListener
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityOrientationBinding
import com.leovp.lib_common_android.exts.*
import com.leovp.lib_reflection.wrappers.ServiceManager
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class OrientationActivity : BaseDemonstrationActivity<ActivityOrientationBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityOrientationBinding {
        return ActivityOrientationBinding.inflate(layoutInflater)
    }

    private var currentDeviceOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private var deviceOrientationEventListener: DeviceOrientationListener? = null

    private val rotationWatcher = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            toast("${rotation.surfaceRotationName}[$rotation]")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceOrientationEventListener = DeviceOrientationListener(this)
        deviceOrientationEventListener?.enable()

        ServiceManager.windowManager?.registerRotationWatcher(rotationWatcher)
        startService(Intent(this, OrientationService::class.java))
    }

    override fun onDestroy() {
        ServiceManager.windowManager?.removeRotationWatcher(rotationWatcher)
        deviceOrientationEventListener?.disable()
        super.onDestroy()
    }

    /** @return
     * [Configuration.ORIENTATION_PORTRAIT],
     * [Configuration.ORIENTATION_LANDSCAPE]
     *
     * constants based on the current phone screen pixel relations.
     */
    private fun getScreenOrientation(): Int {
        val dm: DisplayMetrics = resources.displayMetrics // Screen rotation effected
        //        LogContext.log.w(ITAG, "dm size: ${dm.widthPixels}x${dm.heightPixels}")
        return if (dm.widthPixels > dm.heightPixels)
            Configuration.ORIENTATION_LANDSCAPE
        else Configuration.ORIENTATION_PORTRAIT
    }

    inner class DeviceOrientationListener(private val ctx: Context) : OrientationEventListener(ctx) {
        @SuppressLint("SetTextI18n")
        override fun onOrientationChanged(degree: Int) {
            // val confOrientation = resources.configuration.orientation
            // LogContext.log.d("orientation=$orientation confOrientation=$confOrientation screenWidth=${ctx.screenWidth}")
            binding.tvOrientationDegree.text = degree.toString()
            binding.tvScreenWidth.text = ctx.screenWidth.toString()
            if (degree == ORIENTATION_UNKNOWN) {
                LogContext.log.w("ORIENTATION_UNKNOWN")
                binding.tvDeviceOrientation.text = "ORIENTATION_UNKNOWN"
                return
            }

            val screenPortraitOrLandscape = getScreenOrientation()
            binding.tvSurfaceRotation.text =
                    "${screenSurfaceRotation.surfaceRotationLiteralName}($screenSurfaceRotation) " +
                            screenSurfaceRotation.surfaceRotationName

            currentDeviceOrientation = getDeviceOrientation(degree, currentDeviceOrientation)
            val screenPortraitOrLandscapeName =
                    if (Configuration.ORIENTATION_PORTRAIT == screenPortraitOrLandscape) {
                        "Portrait"
                    } else {
                        "Landscape"
                    }
            //            LogContext.log.w("Device Orientation=${currentDeviceOrientation.screenOrientationName} " +
            //                    "screenSurfaceRotation=${screenSurfaceRotation.surfaceRotationName} " +
            //                    "screenPortraitOrLandscape=$screenPortraitOrLandscapeName")
            binding.tvDeviceOrientation.text = currentDeviceOrientation.screenOrientationName
        }
    }
}