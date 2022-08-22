package com.leovp.android.exts

import android.util.Size

/**
 * Author: Michael Leo
 * Date: 2022/5/30 10:29
 */
/**
 * Compute the greatest common divisor of two values.
 * @param a one value
 * @param b another value
 * @return the greatest common divisor of a and b
 */
fun gcd(a: Int, b: Int): Int {
    if (a < 0 || b < 0) throw IllegalArgumentException("Both $a and $b must be greater than 0.")
    return if (b == 0) a else gcd(b, a % b)
}

fun getRatio(size: Size, delimiters: String = ":", swapResult: Boolean = false): String {
    return com.leovp.kotlin.exts.getRatio(size.width, size.height, delimiters, swapResult)
}

fun getRatio(size: SmartSize, delimiters: String = ":", swapResult: Boolean = false): String {
    return com.leovp.kotlin.exts.getRatio(size.long, size.short, delimiters, swapResult)
}
