package com.leovp.circle_progressbar.util

import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.TypedValue
import java.io.Serializable

/**
 * Author: Michael Leo
 * Date: 2022/1/28 15:12
 */
internal inline fun <reified T : Number> Resources.dp2px(dipValue: Float): T =
        px(TypedValue.COMPLEX_UNIT_DIP, dipValue)

internal inline fun <reified T : Number> Resources.sp2px(spValue: Float): T =
        px(TypedValue.COMPLEX_UNIT_SP, spValue)

internal inline fun <reified T : Number> Resources.px(unit: Int = TypedValue.COMPLEX_UNIT_DIP,
    value: Float): T {
    val result: Float = TypedValue.applyDimension(unit, value, displayMetrics)
    return when (T::class) {
        Float::class -> result as T
        Int::class   -> result.toInt() as T
        else         -> throw IllegalArgumentException("Type not supported")
    }
}

internal inline fun <reified T : Serializable> Bundle.getSerializableOrNull(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializable(key) as? T
        }

internal inline fun <reified T : Parcelable> Bundle.getParcelableOrNull(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelable(key) as? T
        }