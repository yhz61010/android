package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.system.AccessibilityUtil
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * https://github.com/PopFisher/AccessibilitySample
 * https://www.jianshu.com/p/27df6983321f
 * https://www.jianshu.com/p/cd1cd53909d7
 */
class SimulatedClickService : AccessibilityService() {

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
        // The package names that you want to monitor which have been set in accessibility_service_config.
        // If you does not set it, it means monitor all application.
//        val packageName: String = event.packageName.toString()
        val eventType: Int = event.eventType
        val className: String? = event.className?.toString()
        LogContext.log.i(TAG, "onAccessibilityEvent [$className]:$eventType")
        // eventType: AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        AccessibilityUtil.init(this)
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
        EventBus.getDefault().unregister(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogContext.log.i(TAG, "onServiceConnected()")
        EventBus.getDefault().register(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LogContext.log.i(TAG, "onUnbind()")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "SCS"
    }
}