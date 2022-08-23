package com.leovp.android.exts

import android.util.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 2022/5/30 10:37
 */

/** Helper class used to pre-compute shortest and longest sides of a [Size] */
class SmartSize(width: Int, height: Int) {
    var size = Size(width, height)
    var long = max(size.width, size.height)
    var short = min(size.width, size.height)
    override fun toString() = "SmartSize(${size.width}x${size.height})"
}

fun Size.toSmartSize(): SmartSize = SmartSize(width, height)
