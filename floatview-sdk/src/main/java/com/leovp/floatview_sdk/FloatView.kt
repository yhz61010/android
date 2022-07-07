@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.floatview_sdk

import android.app.Activity
import android.view.View
import com.leovp.floatview_sdk.entities.DefaultConfig
import com.leovp.floatview_sdk.entities.FloatViewAttribute
import com.leovp.floatview_sdk.entities.GlobalConfig
import com.leovp.floatview_sdk.framework.FloatViewManager

/**
 * Author: Michael Leo
 * Date: 2021/8/26 10:27
 *
 * **Attention**:
 * By using `FloatView`, you can still drag the whole float view even you drag on a button or any other view.
 * However, the view in layout can **NOT** have an empty `onClickListener` otherwise you can't drag on it.
 *
 * Need permission: `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
 *
 * @see [Easy Float](https://github.com/princekin-f/EasyFloat)
 * @see [Float View](https://stackoverflow.com/a/53092436)
 * @see [Float View Github](https://github.com/aminography/FloatingWindowApp)
 */
class FloatView internal constructor(internal val context: Activity) {

    companion object {
        /** Create new FloatView. */
        fun with(context: Activity): FloatViewCreator = FloatViewCreator(FloatView(context))

        fun exist(tag: String = DefaultConfig.DEFAULT_FLOAT_VIEW_TAG): Boolean =
                FloatViewManager.exist(tag)

        /** Get the the specified FloatView by tag name in order to change its attribute. */
        fun with(tag: String, init: (FloatViewAttribute.() -> Unit)? = null): FloatViewAttribute {
            val attr = FloatViewAttribute(tag)
            init?.let { attr.init() }
            return attr
        }

        /** Get the default FloatView with default tag name in order to change its attribute. */
        fun default(): FloatViewAttribute = with(DefaultConfig.DEFAULT_FLOAT_VIEW_TAG)

        fun visibleAll() = FloatViewManager.visibleAll(true)
        fun invisibleAll() = FloatViewManager.visibleAll(false)

        /**
         * **Destroy** all float views.
         */
        fun clearAll() = FloatViewManager.clear()

        internal var globalConfig = GlobalConfig()

        fun defaultConfig(init: GlobalConfig.() -> Unit) {
            globalConfig.init()
        }
    }

    interface TouchEventListener {
        /**
         * If [DefaultConfig.fullScreenFloatView] is `true` or [DefaultConfig.enableDrag] is `false`,
         * the return result will be ignored. It'll always `false`.
         */
        fun touchDown(view: View, x: Int, y: Int): Boolean = false

        /**
         * If [DefaultConfig.fullScreenFloatView] is `true` or [DefaultConfig.enableDrag] is `false`,
         * the return result will be ignored. It'll always `false`.
         */
        fun touchMove(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean = true

        /**
         * Generally, if [isClickGesture] is `false` that means before touch up being triggered,
         * user is just moving the view. At this time, we should consume this touch event
         * so the click listener that use set should NOT be triggered.
         *
         * In contrast, if [isClickGesture] is `true` that means user triggers the click event,
         * so this touch event should not be consumed.
         *
         * If [DefaultConfig.fullScreenFloatView] is `true` or [DefaultConfig.enableDrag] is `false`,
         * the return result will be ignored. It'll always `false`.
         */
        fun touchUp(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean = !isClickGesture
    }
}