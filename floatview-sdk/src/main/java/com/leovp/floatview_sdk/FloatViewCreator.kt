package com.leovp.floatview_sdk

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import com.leovp.floatview_sdk.entities.DefaultConfig
import com.leovp.floatview_sdk.framework.FloatViewManager
import com.leovp.floatview_sdk.utils.FloatViewScopeMarker
import com.leovp.floatview_sdk.utils.canDrawOverlays

/**
 * Author: Michael Leo
 * Date: 2022/7/7 10:18
 */
@FloatViewScopeMarker
class FloatViewCreator internal constructor(floatingView: FloatView) {
    private val context = floatingView.context

    private var config = DefaultConfig(globalConfig = FloatView.globalConfig.copy())

    fun meta(init: DefaultConfig.() -> Unit): FloatViewCreator = apply { config.init() }

    //        fun asFloatView(): FloatView = floatingView

    fun layout(@LayoutRes layoutId: Int, handle: (view: View) -> Unit): FloatViewCreator = apply {
        config.customView = LayoutInflater.from(context).inflate(layoutId, null).also {
            handle.invoke(it)
        }
    }

    /**
     * If the [DefaultConfig#touchable] is `false`, this listener will not be triggered.
     * Because the FLAG_NOT_TOUCHABLE will be added and it will bubble the event to the bottom layer.
     * So the float layer itself can not be touched anymore.
     */
    fun listener(touchListener: FloatView.TouchEventListener): FloatViewCreator =
            apply { config.touchEventListener = touchListener }

    /**
     * Create float view but don't add it to the window manager.
     */
    fun build(): Unit = FloatViewManager.create(context, config)

    /**
     * **Create** a float view with specific tag and show it on screen.
     */
    fun show() {
        if (config.systemWindow) {
            if (context.canDrawOverlays) createAndShowFloatView()
        } else {
            createAndShowFloatView()
        }
    }

    private fun createAndShowFloatView() {
        build()
        FloatViewManager.show(config.tag)
    }
}