package com.leovp.androidbase.utils.notch.impl

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.text.TextUtils
import com.leovp.androidbase.utils.device.DeviceProp
import com.leovp.androidbase.utils.device.DeviceUtil.isPortrait
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.INotchScreen.NotchSizeCallback
import java.util.*

@TargetApi(Build.VERSION_CODES.O)
class OppoNotchScreen : INotchScreen {
    override fun hasNotch(activity: Activity): Boolean {
        return runCatching { activity.packageManager.hasSystemFeature("com.oppo.feature.screen.heteromorphism") }.getOrDefault(false)
    }

    override fun setDisplayInNotch(activity: Activity) = Unit

    override fun getNotchRect(activity: Activity, callback: NotchSizeCallback) {
        runCatching {
            val notchPosition = notchPosition
            if (!TextUtils.isEmpty(notchPosition)) {
                val split = notchPosition.split(":".toRegex()).toTypedArray()
                val leftTopPoint = split[0]
                val leftAndTop = leftTopPoint.split(",".toRegex()).toTypedArray()
                val rightBottomPoint = split[1]
                val rightAndBottom = rightBottomPoint.split(",".toRegex()).toTypedArray()
                val left: Int
                val top: Int
                val right: Int
                val bottom: Int
                if (isPortrait(activity)) {
                    left = Integer.valueOf(leftAndTop[0])
                    top = Integer.valueOf(leftAndTop[1])
                    right = Integer.valueOf(rightAndBottom[0])
                    bottom = Integer.valueOf(rightAndBottom[1])
                } else {
                    left = Integer.valueOf(leftAndTop[1])
                    top = Integer.valueOf(leftAndTop[0])
                    right = Integer.valueOf(rightAndBottom[1])
                    bottom = Integer.valueOf(rightAndBottom[0])
                }
                val rect = Rect(left, top, right, bottom)
                val rects = ArrayList<Rect>()
                rects.add(rect)
                callback.onResult(rects)
            }
        }.onFailure { it.printStackTrace() }
    }

    companion object {
        /**
         * Get notch position
         *
         * @return The result is like "0,0:104,72" which means the top left position and bottom right position
         */
        private val notchPosition = DeviceProp.getSystemProperty("ro.oppo.screen.heteromorphism")
    }
}