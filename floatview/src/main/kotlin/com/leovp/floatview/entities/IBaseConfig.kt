package com.leovp.floatview.entities

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
     * It is recommended to initialize the proper screen orientation when creating a float view.
     * Otherwise, the float view may be displayed in an unexpected position on Android 12+
     * when your app is in the background or the float view has just been created after
     * a screen orientation change.
     *
     * Surface.ROTATION_0
     * Surface.ROTATION_90
     * Surface.ROTATION_180
     * Surface.ROTATION_270
     */
    var screenOrientation: Int

    /**
     * When true, the float view adjusts position based on device physical
     * orientation even when screen rotation is locked by the user.
     * This uses OrientationEventListener (accelerometer sensor).
     * Default: false (only follow actual screen rotation).
     */
    var followDeviceOrientation: Boolean
}
