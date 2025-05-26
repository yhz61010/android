package com.leovp.demo.basiccomponents.examples.orientation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.IRotationWatcher
import android.view.OrientationEventListener
import com.leovp.android.exts.getDeviceOrientation
import com.leovp.android.exts.screenOrientationName
import com.leovp.android.exts.screenSurfaceRotation
import com.leovp.android.exts.screenWidth
import com.leovp.android.exts.surfaceRotationLiteralName
import com.leovp.android.exts.surfaceRotationName
import com.leovp.android.exts.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityOrientationBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.reflection.wrappers.ServiceManager

class OrientationActivity : BaseDemonstrationActivity<ActivityOrientationBinding>(R.layout.activity_orientation) {
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

    inner class DeviceOrientationListener(private val ctx: Context) : OrientationEventListener(ctx) {
        @SuppressLint("SetTextI18n")
        override fun onOrientationChanged(degree: Int) {
            // val confOrientation = resources.configuration.orientation
            // LogContext.log.d("orientation=$orientation confOrientation=$confOrientation screenWidth=${ctx.screenWidth}")
            binding.tvOrientationDegree.text = degree.toString()
            binding.tvScreenWidth.text = ctx.screenWidth.toString()
            if (degree == ORIENTATION_UNKNOWN) {
                LogContext.log.w(tag, "ORIENTATION_UNKNOWN")
                binding.tvDeviceOrientation.text = "ORIENTATION_UNKNOWN"
                return
            }

            val surRotationLiteral = screenSurfaceRotation.surfaceRotationLiteralName
            val surRotationName = screenSurfaceRotation.surfaceRotationName
            binding.tvSurfaceRotation.text = "$surRotationLiteral($screenSurfaceRotation) $surRotationName"

            currentDeviceOrientation = getDeviceOrientation(degree, currentDeviceOrientation)
            val screenOrientationName = currentDeviceOrientation.screenOrientationName
            LogContext.log.w(
                tag,
                "Device Orientation=$screenOrientationName screenSurfaceRotation=$surRotationName "
            )
            binding.tvDeviceOrientation.text = screenOrientationName
        }
    }
}
