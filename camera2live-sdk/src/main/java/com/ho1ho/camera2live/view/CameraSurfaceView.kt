package com.ho1ho.camera2live.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceView
import com.ho1ho.androidbase.utils.LLog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午16:00
 */
class CameraSurfaceView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0) :
    SurfaceView(context, attrs, defStyle) {

    private var aspectRatio = 0f

    /**
     * **Attention**: The parameter values are the camera orientation. NOT the device orientation.
     */
    fun setDimension(width: Int, height: Int) {
        require(!(width < 0 || height < 0)) { "Size cannot be negative." }
        val realWidth: Int
        val realHeight: Int
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            realWidth = width
            realHeight = height
            aspectRatio = width.toFloat() / height.toFloat()
        } else {
            realWidth = min(width, height)
            realHeight = max(width, height)
            aspectRatio = realHeight.toFloat() / realWidth.toFloat()
        }
        LLog.d(TAG, "setDimension width=$width height=$height ratio=$aspectRatio")

        holder.setFixedSize(realWidth, realHeight)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        LLog.d(TAG, "onMeasure width=$width height=$height aspectRatio=$aspectRatio")
        if (0f == aspectRatio) {
            setMeasuredDimension(width, height)
        } else {
            // Performs center-crop transformation of the camera frames
            val newWidth: Int
            val newHeight: Int
            val actualRatio = if (width > height) aspectRatio else 1f / aspectRatio
            if (width < height * actualRatio) {
                newHeight = height
                newWidth = (height * actualRatio).roundToInt()
            } else {
                newWidth = width
                newHeight = (width / actualRatio).roundToInt()
            }
            LLog.d(TAG, "setMeasuredDimension newWidth=$newWidth newHeight=$newHeight aspectRatio=$aspectRatio")
            setMeasuredDimension(newWidth, newHeight)
        }
    }

    companion object {
        private const val TAG = "CameraSurfaceView"
    }
}