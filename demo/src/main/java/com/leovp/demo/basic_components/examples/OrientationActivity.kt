package com.leovp.demo.basic_components.examples

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.OrientationEventListener
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityOrientationBinding
import com.leovp.lib_common_android.exts.*
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class OrientationActivity : BaseDemonstrationActivity<ActivityOrientationBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityOrientationBinding {
        return ActivityOrientationBinding.inflate(layoutInflater)
    }

    private var currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private var screenOrientationEventListener: ScreenOrientationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenOrientationEventListener = ScreenOrientationListener(this)
    }

    override fun onResume() {
        super.onResume()
        screenOrientationEventListener?.enable()
    }

    override fun onPause() {
        super.onPause()
        screenOrientationEventListener?.disable()
    }

    inner class ScreenOrientationListener(private val ctx: Context) : OrientationEventListener(ctx) {
        @SuppressLint("SetTextI18n")
        override fun onOrientationChanged(orientation: Int) {
            LogContext.log.d("orientation=$orientation")
            binding.tvOrientationDegree.text = orientation.toString()
            binding.tvScreenWidth.text = ctx.getScreenWidth().toString()
            val newOrientation = getScreenOrientation(orientation)
            if (orientation == ORIENTATION_UNKNOWN || newOrientation == ORIENTATION_UNKNOWN) {
                LogContext.log.w("ORIENTATION_UNKNOWN")
                binding.tvDirection.text = "ORIENTATION_UNKNOWN"
                return
            }
            //            if (currentScreenOrientation == newOrientation) return
            @Suppress("DEPRECATION")
            val currentScreenSurfaceRotation = getDeviceSurfaceRotation()
            binding.tvSurfaceRotation.text = currentScreenSurfaceRotation.toString()

            when {
                isNormalPortrait(orientation)   -> {
                    LogContext.log.w("Orientation=Portrait")
                    binding.tvDirection.text = "Portrait"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                isReversePortrait(orientation)  -> {
                    LogContext.log.w("Orientation=ReversePortrait")
                    binding.tvDirection.text = "ReversePortrait"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
                isNormalLandscape(orientation)  -> {
                    LogContext.log.w("Orientation=Landscape")
                    binding.tvDirection.text = "Landscape"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                isReverseLandscape(orientation) -> {
                    LogContext.log.w("Orientation=ReverseLandscape")
                    binding.tvDirection.text = "ReverseLandscape"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }
        }
    }
}