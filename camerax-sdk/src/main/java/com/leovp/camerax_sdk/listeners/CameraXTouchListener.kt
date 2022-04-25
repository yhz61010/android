package com.leovp.camerax_sdk.listeners

/**
 * Author: Michael Leo
 * Date: 2022/4/25 09:50
 */
interface CameraXTouchListener {
    fun onSingleTap(x: Float, y: Float)
    fun onDoubleTap(x: Float, y: Float)
    fun onZoom(ratio: Float)
    fun onScale(scale: Float)
}