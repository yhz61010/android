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
}