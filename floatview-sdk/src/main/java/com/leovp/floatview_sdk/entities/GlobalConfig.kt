package com.leovp.floatview_sdk.entities

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/7/7 10:27
 */
@Keep
data class GlobalConfig(
    override var immersiveMode: Boolean = false,
    override var stickyEdge: StickyEdge = StickyEdge.NONE,
    override var edgeMargin: Int = DEFAULT_EDGE_MARGIN,
    override var dockEdge: DockEdge = DockEdge.NONE,
    override var dockAnimDuration: Long = 200, // ms
    override var touchToleranceInPx: Int = TOUCH_TOLERANCE_IN_PX,
    override var enableAlphaAnimation: Boolean = ENABLE_ALPHA_ANIMATION,
    override var screenOrientation: Int = -1
) : IBaseConfig {
    companion object {
        const val DEFAULT_EDGE_MARGIN = 0 // px
        const val TOUCH_TOLERANCE_IN_PX = 8
        const val ENABLE_ALPHA_ANIMATION = false
    }
}

enum class StickyEdge {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

enum class DockEdge {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    LEFT_RIGHT,
    TOP_BOTTOM,
    FULL
}
