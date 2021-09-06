package com.leovp.floatview_sdk

import android.app.Activity
import android.graphics.Point
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import com.leovp.floatview_sdk.base.AutoDock
import com.leovp.floatview_sdk.base.DefaultConfig
import com.leovp.floatview_sdk.base.StickyEdge
import com.leovp.floatview_sdk.util.canDrawOverlays


/**
 * Author: Michael Leo
 * Date: 2021/8/26 10:27
 *
 * Need permission: `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
 *
 * @see [Easy Float](https://github.com/princekin-f/EasyFloat)
 * @see [Float View](https://stackoverflow.com/a/53092436)
 * @see [Float View Github](https://github.com/aminography/FloatingWindowApp)
 */
@Suppress("unused")
class FloatView private constructor(private val context: Activity) {

    companion object {
        fun with(context: Activity): FloatViewCreator = FloatViewCreator(FloatView(context))
        fun exist(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Boolean = FloatViewManager.exist(tag)
        fun getCustomLayout(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): View? = FloatViewManager.getConfig(tag)?.customView
        fun setEnableDrag(enable: Boolean, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) {
            FloatViewManager.getConfig(tag)?.enableDrag = enable
        }

        /**
         * In some cases, you may just want to mask a full screen transparent float window and you can show finger paint on screen.
         * Meanwhile, you can still touch screen and pass through the float window to the bottom layer just like no that float window.
         * In this case, you should set touchable status to `false`.
         */
        fun setTouchable(touchable: Boolean, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) {
            FloatViewManager.getConfig(tag)?.touchable = touchable
            show(tag)
        }

        /**
         * Show a float view with specific tag.
         * Attention: The float view should be exist which is created by calling `build()` method like this [FloatView.with(ctx).build()]
         */
        fun show(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) {
            if (FloatViewManager.exist(tag)) {
                FloatViewManager.show(tag)
            }
        }

        /**
         *  **Destroy** the specific float view.
         */
        fun dismiss(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.dismiss(tag)

        /**
         * Make float view be visible. Note that, the target float view should be created before.
         */
        fun visible(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.visible(tag, true)

        /**
         * Make float view be invisible. Note that, the target float view should be created before.
         */
        fun invisible(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.visible(tag, false)
        fun isShowing(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Boolean = FloatViewManager.getConfig(tag)?.isShowing ?: false

        /**
         * **Destroy** all float views.
         */
        fun clear() = FloatViewManager.clear()

        fun getX(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Int = FloatViewManager.getFloatViewImpl(tag)?.layoutParams?.x ?: 0
        fun getY(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Int = FloatViewManager.getFloatViewImpl(tag)?.layoutParams?.y ?: 0
        fun getPosition(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Point = Point(getX(tag), getY(tag))

        fun setX(x: Int, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(x, null)
        fun setY(y: Int, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(null, y)
        fun setPosition(x: Int, y: Int, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(x, y)

        fun setStickyEdge(stickyEdge: StickyEdge, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.getFloatViewImpl(tag)?.updateStickyEdge(stickyEdge)
        fun setAutoDock(autoDock: AutoDock, tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG) = FloatViewManager.getFloatViewImpl(tag)?.updateAutoDock(autoDock)

        fun getFloatViewWidth(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Int = FloatViewManager.getFloatViewWidth(tag)
        fun getFloatViewHeight(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Int = FloatViewManager.getFloatViewHeight(tag)
        fun getFloatViewSize(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Size = Size(getFloatViewWidth(tag), getFloatViewHeight(tag))
    }

    class FloatViewCreator internal constructor(floatingView: FloatView) {
        private val context = floatingView.context
        private val config = DefaultConfig()

//        fun asFloatView(): FloatView = floatingView

        fun setLayout(@LayoutRes layoutId: Int, handle: (view: View) -> Unit) = apply {
            config.customView = LayoutInflater.from(context).inflate(layoutId, null)
            handle.invoke(config.customView!!)
        }

        fun setTag(tag: String) = apply { config.floatViewTag = tag }
        fun setEnableDrag(enable: Boolean) = apply { config.enableDrag = enable }
        fun setEnableFullScreenFloatView(enable: Boolean) = apply { config.fullScreenFloatView = enable }
        fun setDragOverStatusBar(enable: Boolean) = apply { config.canDragOverStatusBar = enable }
        fun setX(x: Int) = apply { config.x = x }
        fun setY(y: Int) = apply { config.y = y }

        /**
         * Sets the edge margin. The default margin is 0px.
         */
        fun setEdgeMargin(margin: Int) = apply { config.edgeMargin = margin }
        fun setStickyEdge(stickyEdge: StickyEdge) = apply { config.stickyEdge = stickyEdge }
        fun setAutoDock(autoDock: AutoDock) = apply { config.autoDock = autoDock }

        /**
         * Sets the length of the animation. The default duration is 200 milliseconds.
         */
        fun setDockAnimDuration(duration: Long) = apply { config.dockAnimDuration = duration }

        fun setTouchEventListener(touchEventListener: TouchEventListener) = apply { config.touchEventListener = touchEventListener }

        /**
         * In some cases, you may just want to mask a full screen transparent float window and you can show finger paint on screen.
         * Meanwhile, you can still touch screen and pass through the float window to the bottom layer just like no that float window.
         * In this case, you should set touchable status to `false`.
         */
        fun setTouchable(touchable: Boolean) = apply { config.touchable = touchable }

        /**
         * Create float view but don't add it to the window manager.
         */
        fun build(): Unit = FloatViewManager.create(context, config)

        /**
         * **Create** a float view with specific tag and show it on screen.
         */
        fun show() {
            if (context.canDrawOverlays) {
                build()
                FloatViewManager.show(config.floatViewTag)
            }
        }
    }

    interface TouchEventListener {
        fun touchDown(view: View, x: Int, y: Int): Boolean = false
        fun touchMove(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean = true

        /**
         * Generally, if [isClickGesture] is `false` that means before touch up being triggered, user is just moving the view.
         * At this time, we should consume this touch event so the click listener that use set should NOT be triggered.
         *
         * In contrast, if [isClickGesture] is `true` that means user triggers the click event,
         * so this touch event should not be consumed.
         */
        fun touchUp(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean = !isClickGesture
    }
}