@file:Suppress("unused")

package com.leovp.floatview_sdk

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import com.leovp.floatview_sdk.entities.DefaultConfig
import com.leovp.floatview_sdk.framework.FloatViewManager
import com.leovp.floatview_sdk.utils.FloatViewScopeMarker
import com.leovp.floatview_sdk.utils.canDrawOverlays

/**
 * Attention:
 * It's better to call `layout` first then call `meta`, so that you can get
 * float view size in `meta`.
 *
 * For example:
 * ```kotlin
 * FloatView.with(ctx)
 *      .layout(R.layout.float_view_layout) { v ->
 *          // Process your custom view.
 *      }
 *      .meta { viewWidth, viewHeight ->
 *          tag = "float_view-tag"
 *          x = (ctx.screenWidth - viewWidth) / 2
 *          y = (ctx.screenRealHeight - viewHeight) / 2
 *      }
 *      .show()
 * ```
 *
 * Author: Michael Leo
 * Date: 2022/7/7 10:18
 */
@FloatViewScopeMarker
class FloatViewCreator internal constructor(floatingView: FloatView) {
    private val context = floatingView.context

    private var config = DefaultConfig(globalConfig = FloatView.globalConfig.copy())

    fun layout(@LayoutRes layoutId: Int, handle: ((view: View) -> Unit)? = null): FloatViewCreator = apply {
        config.customView = LayoutInflater.from(context).inflate(layoutId, null).also {
            handle?.invoke(it)
        }
    }

    fun layout(view: View, handle: ((view: View) -> Unit)? = null): FloatViewCreator = apply {
        config.customView = view.also { handle?.invoke(it) }
    }

    //    fun meta(init: DefaultConfig.() -> Unit): FloatViewCreator = apply { config.init() }

    fun meta(init: DefaultConfig.(viewWidth: Int, viewHeight: Int) -> Unit): FloatViewCreator {
        val customView: View? = config.customView
        return apply {
            if (customView == null) {
                config.init(DefaultConfig.DEFAULT_X_POS, DefaultConfig.DEFAULT_Y_POS)
            } else {
                customView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                // customView.layout(0, 0, customView.measuredWidth, customView.measuredHeight)
                config.init(customView.measuredWidth, customView.measuredHeight)
            }
        }
    }

    //        fun asFloatView(): FloatView = floatingView

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
            if (context.canDrawOverlays) {
                createAndShowFloatView()
            }
        } else {
            createAndShowFloatView()
        }
    }

    private fun createAndShowFloatView() {
        build()
        FloatViewManager.show(config.tag)
    }
}