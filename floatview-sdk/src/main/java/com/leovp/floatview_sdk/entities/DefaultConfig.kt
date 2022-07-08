package com.leovp.floatview_sdk.entities

import android.view.View
import androidx.annotation.Keep
import com.leovp.floatview_sdk.FloatView
import com.leovp.floatview_sdk.entities.GlobalConfig.Companion.DEFAULT_EDGE_MARGIN

/**
 * Note that, all the configurations here can only be set when construct FloatView.
 *
 * If you want to change some properties value, see the [FloatViewAttribute]
 * in where you can find all the values can be modified.
 *
 * Author: Michael Leo
 * Date: 2021/8/30 10:58
 */
@Keep
data class DefaultConfig(
    internal var customView: View? = null,

    var tag: String = DEFAULT_FLOAT_VIEW_TAG,

    /**
     * If set this property to `true`, the float view can be shown over system.
     */
    var systemWindow: Boolean = false,

    /**
     * It's better to set a proper value in here.
     * Otherwise if this value is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     */
    var x: Int = DEFAULT_EDGE_MARGIN,

    /**
     * It's better to set a proper value in here.
     * Otherwise if this value is out of screen dimension,
     * it will be modified to proper value after float view has already been shown.
     */
    var y: Int = DEFAULT_EDGE_MARGIN,
    var fullScreenFloatView: Boolean = false,
    var enableDrag: Boolean = true,

    /**
     * If the [touchable] is `false`, this listener will not be triggered.
     * Because the FLAG_NOT_TOUCHABLE will be added and it will bubble the event to the bottom layer.
     * So the float layer itself can not be touched anymore.
     */
    internal var touchEventListener: FloatView.TouchEventListener? = null,

    var isDisplaying: Boolean = false,

    /**
     * In some cases, you may just want to mask a full screen transparent float window and you can show finger paint on screen.
     * Meanwhile, you can still touch screen and pass through the float window to the bottom layer just like no that float window.
     * In this case, you should set touchable status to `false`.
     */
    var touchable: Boolean = true,

    internal val globalConfig: GlobalConfig
) : IBaseConfig by globalConfig {
    companion object {
        const val DEFAULT_FLOAT_VIEW_TAG = "tag_default_float_view"
    }
}