package com.leovp.kotlin.exts

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Author: Michael Leo
 * Date: 20-12-14 上午10:52
 */
fun Double.round(precision: Int = 2, roundingMode: RoundingMode = RoundingMode.HALF_UP): Double {
    require(precision >= 0) { "precision must be >= 0 but was $precision" }
    // Non-finite values have no fixed-point representation; DecimalFormat renders them as "∞"/"NaN",
    // which toDouble() then fails to parse. Return them unchanged (remediation H15).
    if (isNaN() || isInfinite()) return this
    // Pin the symbols to Locale.ENGLISH; under a locale whose decimal separator is ',' (e.g. Germany)
    // the formatted string would otherwise be unparseable by toDouble() (remediation H15).
    val df = DecimalFormat("#.${"#".repeat(precision)}", DecimalFormatSymbols(Locale.ENGLISH))
    df.roundingMode = roundingMode
    return df.format(this).toDouble()
}

fun Float.round(precision: Int = 2, roundingMode: RoundingMode = RoundingMode.HALF_UP): Float =
    this.toDouble().round(precision, roundingMode).toFloat()
