package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SimulatedClickService : AccessibilityService() {

    override fun onCreate() {
        LogContext.log.i(TAG, "onCreate()")
        super.onCreate()
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        LogContext.log.i(TAG, "onDestroy()")
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onReceiveEvent(touchBean: ScreenShareClientActivity.TouchBean) {
        LogContext.log.i(TAG, "onReceiveEvent: ${touchBean.toJsonString()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dispatchGestureClick(touchBean.x.toInt(), touchBean.y.toInt())
        } else {
            LogContext.log.w(TAG, "Does NOT support simulate click.")
        }
    }

    // Attention: this callback is calling from main thread.
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType: Int = event.eventType
        LogContext.log.i(TAG, "onAccessibilityEvent: $eventType")
        // eventType: AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatchGestureClick(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val click: Boolean = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null
        )
        LogContext.log.i(TAG, "dispatchGestureClick: $click")
    }

    override fun onInterrupt() {
        LogContext.log.i(TAG, "onInterrupt()")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogContext.log.i(TAG, "onServiceConnected()")
    }

    companion object {
        private const val TAG = "SCS"
    }
}