package com.ho1ho.camera2live.view

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import com.ho1ho.androidbase.utils.CLog

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午16:00
 */
class CameraTextureView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0) :
    TextureView(context, attrs, defStyle) {
    private var mSpecifiedWidth = 0
    private var mSpecifiedHeight = 0

    fun setDimension(width: Int, height: Int) {
        require(!(width < 0 || height < 0)) { "Size cannot be negative." }
        mSpecifiedWidth = width
        mSpecifiedHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        CLog.d(TAG, "onMeasure width=$width height=$height")
        if (0 == mSpecifiedWidth || 0 == mSpecifiedHeight) {
            setMeasuredDimension(width, height)
        } else {
            setMeasuredDimension(mSpecifiedWidth, mSpecifiedHeight)
        }
    }

    companion object {
        private const val TAG = "CameraTextureView"
    }
}