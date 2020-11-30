package com.leovp.androidbase.exts.android.utils

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import java.util.*


/**
 * A set of color-related utility methods
 */
class ColorUtil(var color: Int) {

    /**
     * 通过十六进制颜色码(hex ∈ [#00000000,#FFFFFFFF])获取颜色
     */
    constructor(hex: String) : this(Color.parseColor(hex))

    /**
     * 通过RGB(r,g,b ∈ [0,255])值获取颜色
     */
    constructor(r: Int, g: Int, b: Int) : this(Color.rgb(r, g, b))

    /**
     * 通过ARGB(a, r,g,b ∈ [0,255])值获取颜色
     */
    constructor(a: Int, r: Int, g: Int, b: Int) : this(Color.argb(a, r, g, b))

    /**
     * 获取[color]的[red]通道值
     */
    val red get() = Color.red(color)

    /**
     * 获取[color]的[blue]通道值
     */
    val blue get() = Color.blue(color)

    /**
     * 获取[color]的[green]通道值
     */
    val green get() = Color.green(color)

    /**
     * [color]的[alpha]通道值
     */
    var alpha
        /**
         * 获取[color]的[alpha]通道值
         */
        get() = Color.alpha(color)
        /**
         * 设置[color]的[alpha]通道值
         */
        set(value) {
            color = ColorUtils.setAlphaComponent(color, value)
        }

    /**
     * 获取[color]的灰阶[0,255]
     * [grayscale] [192,255] <=> 浅色; [0,192] 深色
     **/
    val grayscale get() = 0.299 * red + 0.114 * blue + 0.587 * green

    /**
     * 获取[color]透过遮罩[mask]所应显示的颜色
     */
    fun composite(mask: Int) = ColorUtils.compositeColors(mask, color)

    /**
     * 获取颜色的十六进制码
     */
    val hex: String get() {
        var hex = Integer.toHexString(color).toUpperCase(Locale.getDefault())
        for (i in hex.length until 8) hex = "0$hex"
        return "#$hex"
    }

}
