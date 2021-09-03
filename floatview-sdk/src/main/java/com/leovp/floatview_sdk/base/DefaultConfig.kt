package com.leovp.floatview_sdk.base

import android.view.View
import com.leovp.floatview_sdk.FloatView

/**
 * Author: Michael Leo
 * Date: 2021/8/30 10:58
 */
internal data class DefaultConfig(
    var customView: View? = null,
    var floatViewTag: String = DEFAULT_FLOAT_VIEW_TAG,
    var x: Int = DEFAULT_EDGE_MARGIN,
    var y: Int = DEFAULT_EDGE_MARGIN,
    var enableDrag: Boolean = true,
    var fullScreenFloatView: Boolean = false,
    var canDragOverStatusBar: Boolean = false,
    var touchEventListener: FloatView.TouchEventListener? = null,
    var isShowing: Boolean = false,
    var stickyEdge: StickyEdge = StickyEdge.NONE,
    var edgeMargin: Int = DEFAULT_EDGE_MARGIN,
    var autoDock: AutoDock = AutoDock.NONE,
    var dockAnimDuration: Long = 200, // ms
    var touchable: Boolean = true,
) {
    companion object {
        const val DEFAULT_FLOAT_VIEW_TAG = "tag_default_float_view"
        const val DEFAULT_EDGE_MARGIN = 0 // px
    }
}

enum class StickyEdge {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

enum class AutoDock {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    LEFT_RIGHT,
    TOP_BOTTOM,
    FULL
}