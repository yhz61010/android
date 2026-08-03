package com.leovp.android.utils

import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import kotlin.math.roundToInt

/**
 * Author: Michael Leo
 * Date: 2022/2/14 17:34
 */
class TouchHelper(private val touchListener: TouchListener) {
    companion object {
        private const val MAX_TOUCH_POINTS = 10
    }

    private val touchMoveTimeInterval = SparseArray<Long>()

    fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        if (pointerCount > MAX_TOUCH_POINTS) {
            //            pointerCount = GlobalConstants.MAX_TOUCH_POINTS
            return true
        }
        // if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(TAG, "M=${event.actionMasked}
        // pointerCount=$pointerCount actionIndex=$actionIndex")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIndex = event.actionIndex
                val activePointerId = event.getPointerId(actionIndex)
                val activePressure = event.getPressure(actionIndex)
                //                trackingPointerId = activePointerId
                touchMoveTimeInterval.put(activePointerId, 0L)
                processTouchDown(event, activePointerId, activePressure)
            }
            MotionEvent.ACTION_MOVE -> processTouchMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> processTouchUp(event)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
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
                MotionEvent.ACTION_MOVE,
                activePointerId,
                event.getX(event.actionIndex).toInt(),
                event.getY(event.actionIndex).toInt(),
                (activePressure * 100).toInt(),
                interval
            )
        }.onFailure { android.util.Log.e("TouchHelper", "touch handler failed", it) }
    }

    private fun processTouchUp(event: MotionEvent) {
        //        if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(
        //            TAG,
        // "processTouchUp count=${event.pointerCount} actionIndex: ${event.actionIndex}
        // ${event.x.roundToInt()} ${event.y.roundToInt()}"
        //        )
        runCatching {
            val activePointerId: Int = event.getPointerId(event.actionIndex)
            //            if (trackingPointerId == activePointerId) {
            touchListener.onEvent(
                MotionEvent.ACTION_UP,
                activePointerId,
                event.getX(event.actionIndex).roundToInt(),
                event.getY(event.actionIndex).roundToInt(),
                -1,
                -1
            )
            //            }
        }.onFailure { android.util.Log.e("TouchHelper", "touch handler failed", it) }
    }

    private fun processCancelTouch(event: MotionEvent) {
        runCatching {
            // Iterate by pointer index and take coordinates with the SAME index. The pointer id
            // (from getPointerId) is only a stable identifier for the payload, never an index into
            // getX/getY — mixing them read the wrong pointer (or threw) on multi-touch (remediation
            // H2). pointerCount is already capped at MAX_TOUCH_POINTS in onTouchEvent.
            for (i in 0 until event.pointerCount) {
                val activePointerId: Int = event.getPointerId(i)
                touchListener.onEvent(
                    MotionEvent.ACTION_UP,
                    activePointerId,
                    event.getX(i).roundToInt(),
                    event.getY(i).roundToInt(),
                    -1,
                    -1
                )
            }
        }.onFailure { android.util.Log.e("TouchHelper", "touch handler failed", it) }
    }

    private fun processTouchDown(event: MotionEvent, activePointerId: Int, activePressure: Float) {
        //        if (GlobalConstants.OUTPUT_LOG) {
        // if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(TAG, "processTouchDown activePointerId:
        // $activePointerId ${event.x.roundToInt()} ${event.y.roundToInt()}")
        //        }
        runCatching {
            // Resolve the pointer index for this id; getX/getY are index-based, not id-based
            // (remediation H2). A stale/absent id yields -1, in which case there is nothing to report.
            val pointerIndex = event.findPointerIndex(activePointerId)
            if (pointerIndex < 0) return@runCatching
            touchListener.onEvent(
                MotionEvent.ACTION_DOWN,
                activePointerId,
                event.getX(pointerIndex).roundToInt(),
                event.getY(pointerIndex).roundToInt(),
                (activePressure * 100).toInt(),
                -1
            )
        }.onFailure { android.util.Log.e("TouchHelper", "touch handler failed", it) }
    }

    interface TouchListener {
        /**
         * @param type MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP
         */
        fun onEvent(
            type: Int,
            activePointerId: Int,
            x: Int,
            y: Int,
            activePressure: Int,
            timeDelta: Long
        )
    }
}
