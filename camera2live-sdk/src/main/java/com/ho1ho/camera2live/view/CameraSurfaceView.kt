package com.ho1ho.camera2live.view

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import com.ho1ho.androidbase.utils.CLog
import kotlin.math.roundToInt

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午16:00
 */
class CameraSurfaceView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0) :
    SurfaceView(context, attrs, defStyle) {

    private var aspectRatio = 0f

    fun setDimension(width: Int, height: Int) {
        require(!(width < 0 || height < 0)) { "Size cannot be negative." }
        aspectRatio = width.toFloat() / height.toFloat()
        CLog.d(TAG, "setDimension width=$width height=$height ratio=$aspectRatio")

        holder.setFixedSize(width, height)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        CLog.d(
            TAG,
            "onMeasure width=$width height=$height aspectRatio=$aspectRatio"
        )
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
            CLog.d(
                TAG,
                "setMeasuredDimension newWidth=$newWidth newHeight=$newHeight aspectRatio=$aspectRatio"
            )
            setMeasuredDimension(newWidth, newHeight)
        }
    }

    companion object {
        private const val TAG = "CameraSurfaceView"
    }
}