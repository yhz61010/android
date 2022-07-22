@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.floatview_sdk.entities

import android.graphics.Point
import android.util.Size
import android.view.View
import androidx.annotation.Keep
import com.leovp.floatview_sdk.framework.FloatViewManager

/**
 * Note that, all the attributes here can be modified after FloatView is created.
 *
 * Author: Michael Leo
 * Date: 2022/7/7 16:23
 */
@Keep
class FloatViewAttribute(private val tag: String) {

    val customView: View? = FloatViewManager.getConfig(tag)?.customView

    /**
     * If `x` value is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     */
    var x: Int
        get() = FloatViewManager.getFloatViewImpl(tag)?.getFloatViewPosition()?.x ?: 0
        set(value) {
            FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(value, null)
        }

    /**
     * If `y` value is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     */
    var y: Int
        get() = FloatViewManager.getFloatViewImpl(tag)?.getFloatViewPosition()?.y ?: 0
        set(value) {
            FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(null, value, true)
        }

    /**
     * If `position` is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     */
    var position: Point
        get() = FloatViewManager.getFloatViewImpl(tag)?.getFloatViewPosition() ?: Point(0, 0)
        set(value) {
            FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(value.x, value.y)
        }

    /**
     * @param x If `x` value is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     * @param y If `y` value is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     */
    fun setPosition(x: Int, y: Int) {
        FloatViewManager.getFloatViewImpl(tag)?.updateFloatViewPosition(x, y)
    }

    /**
     * Show a float view with specific tag.
     * Attention: The float view should be exist which is created by calling `build()` method like this [FloatView.with(ctx).build()]
     */
    fun show() {
        if (FloatViewManager.exist(tag)) {
            FloatViewManager.show(tag)
        }
    }

    /**
     *  **Destroy** the specific float view.
     */
    fun remove() = FloatViewManager.dismiss(tag)

    /**
     * Make float view be visible. Note that, the target float view should be created before.
     */
    fun visible() = FloatViewManager.visible(tag, true)

    /**
     * Make float view be invisible. Note that, the target float view should be created before.
     */
    fun invisible() = FloatViewManager.visible(tag, false)

    fun isDisplaying(): Boolean = FloatViewManager.getConfig(tag)?.isDisplaying ?: false

    /**
     * In some cases, you may just want to mask a full screen transparent float window and you can show finger paint on screen.
     * Meanwhile, you can still touch screen and pass through the float window to the bottom layer just like no that float window.
     * In this case, you should set touchable status to `false`.
     */
    var touchable: Boolean
        get() = FloatViewManager.getConfig(tag)?.touchable ?: false
        set(value) {
            FloatViewManager.getConfig(tag)?.touchable = value
            show()
        }

    var enableDrag: Boolean
        get() = FloatViewManager.getConfig(tag)?.enableDrag ?: false
        set(value) {
            FloatViewManager.getConfig(tag)?.enableDrag = value
        }

    var stickyEdge: StickyEdge
        get() = FloatViewManager.getFloatViewImpl(tag)?.config?.stickyEdge ?: StickyEdge.NONE
        set(value) {
            FloatViewManager.getFloatViewImpl(tag)?.updateStickyEdge(value)
        }

    var dockEdge: DockEdge
        get() = FloatViewManager.getFloatViewImpl(tag)?.config?.dockEdge ?: DockEdge.NONE
        set(value) {
            FloatViewManager.getFloatViewImpl(tag)?.updateAutoDock(value)
        }

    val floatViewWidth: Int = FloatViewManager.getFloatViewWidth(tag)
    val floatViewHeight: Int = FloatViewManager.getFloatViewHeight(tag)
    val floatViewSize: Size = Size(floatViewWidth, floatViewHeight)
}