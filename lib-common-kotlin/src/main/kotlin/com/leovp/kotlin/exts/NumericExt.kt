package com.leovp.kotlin.exts

/**
 * Author: Michael Leo
 * Date: 2022/5/30 09:08
 */

// https://zh.wikipedia.org/wiki/%E8%BC%BE%E8%BD%89%E7%9B%B8%E9%99%A4%E6%B3%95
// https://stackoverflow.com/a/4009247/1685062

/**
 * Compute the greatest common divisor of two values.
 * @param a one value
 * @param b another value
 * @return the greatest common divisor of a and b
 */
fun gcd(a: Int, b: Int): Int {
    require(a > -1 && b > -1) { "Both $a and $b must be greater than 0." }
    return if (b == 0) a else gcd(b, a % b)
}

fun getRatio(a: Int, b: Int, delimiters: String = ":", swapResult: Boolean = false): String? = runCatching {
    val gcd = gcd(a, b)
    val firstVal = if (swapResult) b / gcd else a / gcd
    val secondVal = if (swapResult) a / gcd else b / gcd
    "$firstVal$delimiters$secondVal"
}.getOrElse {
    // In case getGDC (a recursively looping method) repeats too many times
    // Log.e(TAG, "Irrational ratio: $a to $b")
    // throw ArithmeticException("Irrational ratio: $a to $b")
    null
}

fun Int.formatDecimalSeparator(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()

fun Long.formatDecimalSeparator(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()
