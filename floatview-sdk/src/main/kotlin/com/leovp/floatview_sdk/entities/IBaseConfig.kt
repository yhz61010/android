package com.leovp.floatview_sdk.entities

/**
 * Author: Michael Leo
 * Date: 2022/7/7 11:03
 */
interface IBaseConfig {
    var immersiveMode: Boolean
    var stickyEdge: StickyEdge

    /**
     * Sets the edge margin. The default margin is 0px.
     */
    var edgeMargin: Int

    var dockEdge: DockEdge

    /**
     * Sets the length of the animation. The default duration is 200 milliseconds.
     */
    var dockAnimDuration: Long

    var touchToleranceInPx: Int

    var enableAlphaAnimation: Boolean

    /**
     * **Recommend** to initialise the proper screen orientation when create float view.
     * Otherwise, the float view may display at unexpected position on Android 12+
     * when your app in background or the float view is just created after screen orientation changed.
     *
     * Surface.ROTATION_0
     * Surface.ROTATION_90
     * Surface.ROTATION_180
     * Surface.ROTATION_270
     */
    var screenOrientation: Int
}
