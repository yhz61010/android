package com.leovp.demo.basic_components.examples

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.OrientationEventListener
import android.widget.TextView
import com.leovp.demo.R
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

    private lateinit var tvOrientation: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvRotationInDisplay: TextView

    private var currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private var screenOrientationEventListener: ScreenOrientationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tvOrientation = findViewById(R.id.tvOrientation)
        tvDirection = findViewById(R.id.tvDirection)
        tvRotationInDisplay = findViewById(R.id.tvRotationInDisplay)

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

    inner class ScreenOrientationListener(ctx: Context) : OrientationEventListener(ctx) {
        @SuppressLint("SetTextI18n")
        override fun onOrientationChanged(orientation: Int) {
            LogContext.log.d("orientation=$orientation")
            tvOrientation.text = orientation.toString()
            val newOrientation = getOrientationByDegree(orientation)
            if (orientation == ORIENTATION_UNKNOWN || newOrientation == ORIENTATION_UNKNOWN) {
                LogContext.log.w("ORIENTATION_UNKNOWN")
                tvDirection.text = "ORIENTATION_UNKNOWN"
                return
            }
//            if (currentScreenOrientation == newOrientation) return
            @Suppress("DEPRECATION")
            val currentScreenRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display!!.rotation else windowManager.defaultDisplay.rotation
            tvRotationInDisplay.text = currentScreenRotation.toString()

            when {
                isNormalPortraitByDegree(orientation) -> {
                    LogContext.log.w("Orientation=Portrait")
                    tvDirection.text = "Portrait"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                isReversePortraitByDegree(orientation) -> {
                    LogContext.log.w("Orientation=ReversePortrait")
                    tvDirection.text = "ReversePortrait"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
                isNormalLandscapeByDegree(orientation) -> {
                    LogContext.log.w("Orientation=Landscape")
                    tvDirection.text = "Landscape"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                isReverseLandscapeByDegree(orientation) -> {
                    LogContext.log.w("Orientation=ReverseLandscape")
                    tvDirection.text = "ReverseLandscape"
                    currentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }
        }
    }
}