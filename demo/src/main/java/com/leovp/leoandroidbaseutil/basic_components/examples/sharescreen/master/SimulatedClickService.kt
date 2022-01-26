package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.system.AccessibilityUtil
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.log_sdk.LogContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * https://github.com/PopFisher/AccessibilitySample
 * https://www.jianshu.com/p/27df6983321f
 * https://www.jianshu.com/p/cd1cd53909d7
 */
class SimulatedClickService : AccessibilityService() {

    override fun onCreate() {
        LogContext.log.i("onCreate()")
        super.onCreate()
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        LogContext.log.i("onDestroy()")
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onReceiveEvent(touchBean: ScreenShareClientActivity.TouchBean) {
        LogContext.log.i(TAG, "onReceiveEvent: ${touchBean.toJsonString()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            when (touchBean.touchType) {
                ScreenShareClientActivity.TouchType.DRAG -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dispatchGestureDrag(touchBean.x, touchBean.y, touchBean.dstX, touchBean.dstY, touchBean.duration)
                    } else {
                        LogContext.log.w(TAG, "Simulate drag only available as of Android 8.0")
                    }
                }
                else -> dispatchGestureClick(touchBean.x.toInt(), touchBean.y.toInt())
            }
        } else {
            LogContext.log.w(TAG, "Simulate click only available as of Android 7.0")
        }
    }

    // Attention: this callback is calling from main thread.
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // The package names that you want to monitor which have been set in accessibility_service_config.
        // If you does not set it, it means monitor all application.
//        val packageName: String = event.packageName.toString()
        val eventType: Int = event.eventType
        val className: String? = event.className?.toString()
        // eventType: AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val st = SystemClock.elapsedRealtimeNanos()
        // FIXME Can I initialized AccessibilityUtil in onCreate()?
        AccessibilityUtil.init(this)
        LogContext.log.i(TAG, "init cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us onAccessibilityEvent [$className]:$eventType")
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatchGestureClick(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val clickGesture = GestureDescription.StrokeDescription(path, 0, 100)
        val click: Boolean = dispatchGesture(GestureDescription.Builder().addStroke(clickGesture).build(), null, null)
        LogContext.log.i(TAG, "dispatchGestureClick: $click")
    }

    // Simulates an L-shaped drag path: 200 pixels right, then 200 pixels down.
    @RequiresApi(Build.VERSION_CODES.O)
    private fun dispatchGestureDrag(srcX: Float, srcY: Float, dstX: Float, dstY: Float, duration: Long) {
        val dragPath = Path().apply {
            moveTo(srcX, srcY)
            lineTo(dstX, dstY)
        }
        val dragGesture = GestureDescription.StrokeDescription(dragPath, 0, duration)
        val drag: Boolean = dispatchGesture(GestureDescription.Builder().addStroke(dragGesture).build(), null, null)
        LogContext.log.i(TAG, "dispatchGestureDrag: $drag")
    }

    // Simulates an L-shaped drag path: 200 pixels right, then 200 pixels down.
    @Suppress("unused")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun doRightThenDownDrag() {
        val dragRightPath = Path().apply {
            moveTo(200f, 200f)
            lineTo(400f, 200f)
        }
        val dragRightDuration = 500L // 0.5 second

        // The starting point of the second path must match
        // the ending point of the first path.
        val dragDownPath = Path().apply {
            moveTo(400f, 200f)
            lineTo(400f, 400f)
        }
        val dragDownDuration = 500L
        GestureDescription.StrokeDescription(
            dragRightPath,
            0L,
            dragRightDuration,
            true
        ).apply {
            continueStroke(dragDownPath, dragRightDuration, dragDownDuration, false)
        }
    }

    override fun onInterrupt() {
        LogContext.log.w(TAG, "onInterrupt()")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogContext.log.w(TAG, "onServiceConnected()")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LogContext.log.w(TAG, "onUnbind()")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "SCS"
    }
}