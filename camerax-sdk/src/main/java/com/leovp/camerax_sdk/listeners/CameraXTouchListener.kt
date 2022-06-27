package com.leovp.camerax_sdk.listeners

/**
 * Author: Michael Leo
 * Date: 2022/4/25 09:50
 */
interface CameraXTouchListener {
    fun onStartFocusing(x: Float, y: Float)
    fun onFocusSuccess()
    fun onFocusFail()

    fun onDoubleTap(x: Float, y: Float, zoomRatio: Float)

    fun onZoomBegin(ratio: Float)
    fun onZoom(ratio: Float)
    fun onZoomEnd(ratio: Float)
}