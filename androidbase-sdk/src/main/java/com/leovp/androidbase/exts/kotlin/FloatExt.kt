package com.leovp.androidbase.exts.kotlin

import androidx.annotation.IntRange
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Author: Michael Leo
 * Date: 20-12-14 上午10:52
 */
fun Double.round(@IntRange(from=1) precision: Int = 2, roundingMode: RoundingMode = RoundingMode.HALF_UP): Double {
    val df = DecimalFormat("#.${"#".repeat(precision)}")
    df.roundingMode = roundingMode
    return df.format(this).toDouble()
}

fun Float.round(@IntRange(from=1) precision: Int = 2, roundingMode: RoundingMode = RoundingMode.HALF_UP): Float {
    return this.toDouble().round(precision, roundingMode).toFloat()
}