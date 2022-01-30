package com.leovp.circle_progressbar.util

import android.content.res.Resources
import android.util.TypedValue

/**
 * Author: Michael Leo
 * Date: 2022/1/28 15:12
 */
inline fun <reified T : Number> Resources.dp2px(dipValue: Float): T = px(TypedValue.COMPLEX_UNIT_DIP, dipValue)

inline fun <reified T : Number> Resources.sp2px(spValue: Float): T = px(TypedValue.COMPLEX_UNIT_SP, spValue)

inline fun <reified T : Number> Resources.px(unit: Int = TypedValue.COMPLEX_UNIT_DIP, value: Float): T {
    val result: Float = TypedValue.applyDimension(unit, value, displayMetrics)
    return when (T::class) {
        Float::class -> result as T
        Int::class   -> result.toInt() as T
        else         -> throw IllegalArgumentException("Type not supported")
    }
}

