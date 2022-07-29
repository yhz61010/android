package com.leovp.demo.basic_components.examples

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
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

        screenOrientationEventListener = ScreenOrientationListener(applicationContext)
        screenOrientationEventListener?.enable()
    }

    override fun onDestroy() {
        screenOrientationEventListener?.disable()
        super.onDestroy()
    }

    /** @return The [Configuration.ORIENTATION_SQUARE], [Configuration.ORIENTATION_PORTRAIT], [Configuration.ORIENTATION_LANDSCAPE] constants based on the current phone screen pixel relations.
     */
    private fun getScreenOrientation(): Int {
        val dm: DisplayMetrics = resources.displayMetrics // Screen rotation effected
        LogContext.log.w(ITAG,
            "dm.widthPixels=${dm.widthPixels} dm.heightPixels=${dm.heightPixels}")
        return if (dm.widthPixels > dm.heightPixels)
            Configuration.ORIENTATION_LANDSCAPE
        else Configuration.ORIENTATION_PORTRAIT
    }

    inner class ScreenOrientationListener(private val ctx: Context) : OrientationEventListener(ctx) {
        @SuppressLint("SetTextI18n")
        override fun onOrientationChanged(orientation: Int) {
            getScreenOrientation()
            val confOrientation = resources.configuration.orientation
            LogContext.log.d("orientation=$orientation confOrientation=$confOrientation")
            binding.tvOrientationDegree.text = orientation.toString()
            binding.tvScreenWidth.text = ctx.screenWidth.toString()
            val newOrientation = getScreenOrientation(orientation)
            if (orientation == ORIENTATION_UNKNOWN || newOrientation == ORIENTATION_UNKNOWN) {
                LogContext.log.w("ORIENTATION_UNKNOWN")
                binding.tvDirection.text = "ORIENTATION_UNKNOWN"
                return
            }
            //            if (currentScreenOrientation == newOrientation) return
            binding.tvSurfaceRotation.text = screenSurfaceRotation.toString()

            when {
                isNormalPortrait(orientation)   -> {
                    LogContext.log.w("Orientation=Portrait deviceSurfaceRotation=$screenSurfaceRotation")
                    binding.tvDirection.text = "Portrait"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                isReversePortrait(orientation)  -> {
                    LogContext.log.w("Orientation=ReversePortrait deviceSurfaceRotation=$screenSurfaceRotation")
                    binding.tvDirection.text = "ReversePortrait"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
                isNormalLandscape(orientation)  -> {
                    LogContext.log.w("Orientation=Landscape deviceSurfaceRotation=$screenSurfaceRotation")
                    binding.tvDirection.text = "Landscape"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                isReverseLandscape(orientation) -> {
                    LogContext.log.w("Orientation=ReverseLandscape deviceSurfaceRotation=$screenSurfaceRotation")
                    binding.tvDirection.text = "ReverseLandscape"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }
        }
    }
}