package com.leovp.androidbase.utils.notch.impl

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.leovp.android.exts.isPortrait
import com.leovp.android.utils.DeviceProp
import com.leovp.androidbase.utils.notch.DisplayCutout

@RequiresApi(Build.VERSION_CODES.O)
internal class OppoDisplayCutout : DisplayCutout {
    override fun supportDisplayCutout(activity: Activity): Boolean {
        return runCatching {
            activity.packageManager.hasSystemFeature("com.oppo.feature.screen.heteromorphism")
        }.getOrDefault(false)
    }

    override fun fillDisplayCutout(activity: Activity) = Unit

    override fun cutoutAreaRect(activity: Activity, callback: DisplayCutout.CutoutAreaRectCallback) {
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
                if (activity.isPortrait) {
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
                val rectList = ArrayList<Rect>()
                rectList.add(rect)
                callback.onResult(rectList)
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
