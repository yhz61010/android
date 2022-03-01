package com.leovp.androidbase.ui

import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import com.leovp.log_sdk.LogContext
import kotlin.math.roundToInt

/**
 * Author: Michael Leo
 * Date: 2022/2/14 17:34
 */
class TouchHelper(private val touchListener: TouchListener) {
    companion object {
        private const val TAG = "GTH"
        private const val MAX_TOUCH_POINTS = 10
    }

    private val touchMoveTimeInterval = SparseArray<Long>()

    fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        if (pointerCount > MAX_TOUCH_POINTS) {
            //            pointerCount = GlobalConstants.MAX_TOUCH_POINTS
            return true
        }
        //        if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(TAG, "M=${event.actionMasked} pointerCount=$pointerCount actionIndex=$actionIndex")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIndex = event.actionIndex
                val activePointerId = event.getPointerId(actionIndex)
                val activePressure = event.getPressure(actionIndex)
                //                trackingPointerId = activePointerId
                touchMoveTimeInterval.put(activePointerId, 0L)
                processTouchDown(event, activePointerId, activePressure)
            }
            MotionEvent.ACTION_MOVE                                  -> processTouchMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP     -> processTouchUp(event)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE    -> {
                LogContext.log.w(TAG, "Action Cancel/Outside. Treat it as Up Event.")
                processCancelTouch(event)
            }
        }
        return true
    }

    private fun processTouchMove(event: MotionEvent) {
        var activePointerId: Int
        var activePressure: Float
        var interval: Long
        var previousTime: Long
        var currentTime: Long

        runCatching {
            activePointerId = event.getPointerId(event.actionIndex)
            activePressure = event.getPressure(event.actionIndex)
            previousTime = touchMoveTimeInterval.get(activePointerId)
            currentTime = SystemClock.elapsedRealtime()
            interval = if (previousTime == 0L) 0L else currentTime - previousTime
            touchMoveTimeInterval.put(activePointerId, currentTime)

            touchListener.onEvent(
                MotionEvent.ACTION_MOVE, activePointerId,
                event.getX(event.actionIndex).toInt(),
                event.getY(event.actionIndex).toInt(),
                (activePressure * 100).toInt(),
                interval
            )
        }.onFailure { it.printStackTrace() }
    }

    private fun processTouchUp(event: MotionEvent) {
        //        if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(
        //            TAG,
        //            "processTouchUp count=${event.pointerCount} actionIndex: ${event.actionIndex} ${event.x.roundToInt()} ${event.y.roundToInt()}"
        //        )
        runCatching {
            val activePointerId: Int = event.getPointerId(event.actionIndex)
            //            if (trackingPointerId == activePointerId) {
            touchListener.onEvent(
                MotionEvent.ACTION_UP, activePointerId, event.getX(event.actionIndex).roundToInt(),
                event.getY(event.actionIndex).roundToInt(), -1, -1
            )
            //            }
        }.onFailure { it.printStackTrace() }
    }

    private fun processCancelTouch(event: MotionEvent) {
        runCatching {
            for (i in 0 until MAX_TOUCH_POINTS) {
                val activePointerId: Int = event.getPointerId(i)
                touchListener.onEvent(
                    MotionEvent.ACTION_UP, activePointerId, event.getX(activePointerId).roundToInt(),
                    event.getY(activePointerId).roundToInt(), -1, -1
                )
            }
        }.onFailure { it.printStackTrace() }
    }

    private fun processTouchDown(event: MotionEvent, activePointerId: Int, activePressure: Float) {
        //        if (GlobalConstants.OUTPUT_LOG) {
        //            if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(TAG, "processTouchDown activePointerId: $activePointerId ${event.x.roundToInt()} ${event.y.roundToInt()}")
        //        }
        runCatching {
            touchListener.onEvent(
                MotionEvent.ACTION_DOWN, activePointerId,
                event.getX(activePointerId).roundToInt(),
                event.getY(activePointerId).roundToInt(),
                (activePressure * 100).toInt(),
                -1
            )
        }.onFailure { it.printStackTrace() }
    }

    interface TouchListener {
        /**
         * @param type MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP
         */
        fun onEvent(type: Int, activePointerId: Int, x: Int, y: Int, activePressure: Int, timeDelta: Long)
    }
}