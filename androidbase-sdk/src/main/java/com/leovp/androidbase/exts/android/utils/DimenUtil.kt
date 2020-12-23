package com.leovp.androidbase.exts.android.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.util.DisplayMetrics
import android.util.TypedValue
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.windowManager
import com.leovp.androidbase.utils.system.API
import kotlin.math.max

class DimenUtil(context: Context) {

    private val resource: Resources = context.resources

    private val metrics: DisplayMetrics = resource.displayMetrics

    /**
     * 屏幕信息
     * point.x为屏幕宽度
     * point.y为屏幕高度
     */
    private val screen: Point by lazy {
        Point().apply {
            val display = app.windowManager.defaultDisplay
            if (API.ABOVE_J_MR1) {
                display.getRealSize(this)
            } else {
                display.getSize(this)
            }
        }
    }

    /**
     * 显示信息
     */
    private val display: Display by lazy { Display(metrics) }


    /**
     * 获取虚拟键的高度，单位px
     * Android内部提供的方法，但某些魔改ROM在隐藏虚拟键时会失效(e.g. One UI)。
     * Returns the height of Navigation Bar.
     */
    val navigation: Int
        get() {
            val id = resource.getIdentifier("navigation_bar_height", "dimen", "android")
            return if (id > 0) resource.getDimensionPixelSize(id) else 0
        }

    /**
     * 获取虚拟键高度，单位px
     * 通过屏幕尺寸减去显示尺寸获得。
     */
    fun navigation(): Int = max(screen.y - display.height(), screen.x - display.width)

    /**
     * 获取状态栏的高度(px)
     * Returns the height of Status Bar.
     */
    val status: Int
        get() {
            val id = resource.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) resource.getDimensionPixelSize(id) else 0
        }

    /**
     * 将单位转换为px
     * Converts an unpacked complex data value holding a dimension to its final floating point value.
     * @param unit [TypedValue]
     * TypedValue.COMPLEX_UNIT_DIP: dp -> px
     * TypedValue.COMPLEX_UNIT_PT:  pt -> px
     * TypedValue.COMPLEX_UNIT_MM:  mm -> px
     * TypedValue.COMPLEX_UNIT_IN:  inch -> px
     */
    @JvmOverloads
    fun px(value: Float, unit: Int = TypedValue.COMPLEX_UNIT_DIP) =
        TypedValue.applyDimension(unit, value, metrics).toInt()

    /**
     * px转dp
     * Converts px to dp.
     */
    fun dp(px: Int) = px / metrics.density

    /**
     * px转pt
     * Converts px to pt.
     */
    fun pt(px: Int) = px / metrics.xdpi / (1.0f / 72)

    /**
     * px转mm
     * Converts px to mm.
     */
    fun mm(px: Int) = px / metrics.xdpi / (1.0f / 25.4f)

    /**
     * px转inch
     * Converts px to inch.
     */
    fun inch(px: Int) = px / metrics.xdpi

    /**
     * 获取内容高度(不包含虚拟键与状态栏的尺寸的屏幕高度)
     */
    val height get() = display.height

    /**
     * 根据条件获取屏幕高度
     * @param status 是否计算状态栏的尺寸
     * @param navigation 是否计算虚拟键的尺寸
     */
    fun height(status: Boolean, navigation: Boolean): Int {
        return when {
            status && navigation -> screen.y
            navigation -> screen.y - this.status
            status -> display.height()
            else -> display.height
        }
    }

    /**
     * 获取内容宽度(不包含虚拟键的尺寸的屏幕宽度)
     */
    val width get() = display.width

    /**
     * 根据条件获取屏幕宽度
     * @param navigation 是否计算虚拟键的尺寸
     */
    fun width(navigation: Boolean): Int = if (navigation) screen.x else display.width

    private inner class Display(metrics: DisplayMetrics) {

        /**
         * 展示宽度，不包含虚拟键，单位px
         */
        val width: Int = metrics.widthPixels

        /**
         * 展示高度，不包含虚拟键与状态栏，单位px
         */
        val height: Int = height() - status

        /**
         * 展示高度，包含状态栏，不包含虚拟键，单位px
         */
        fun height(): Int = metrics.heightPixels

    }

}