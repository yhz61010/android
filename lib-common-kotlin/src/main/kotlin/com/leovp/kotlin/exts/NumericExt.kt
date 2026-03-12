@file:Suppress("unused")
package com.leovp.kotlin.exts

import java.math.BigDecimal

/**
 * Author: Michael Leo
 * Date: 2022/5/30 09:08
 */

// https://zh.wikipedia.org/wiki/%E8%BC%BE%E8%BD%89%E7%9B%B8%E9%99%A4%E6%B3%95
// https://stackoverflow.com/a/4009247/1685062

/**
 * Compute the greatest common divisor of two values using the Euclidean algorithm.
 *
 * Both values must be non-negative, otherwise an [IllegalArgumentException] is thrown.
 *
 * @param a one value
 * @param b another value
 * @return the greatest common divisor of [a] and [b]
 *
 * Example:
 * ```
 * gcd(12, 8)   // 4
 * gcd(100, 75) // 25
 * gcd(7, 0)    // 7
 * gcd(0, 0)    // 0
 * ```
 */
fun gcd(a: Int, b: Int): Int {
    require(a > -1 && b > -1) { "Both $a and $b must be greater than 0." }
    return if (b == 0) a else gcd(b, a % b)
}

/**
 * Compute the simplified ratio of two integers separated by the given [delimiters].
 *
 * @param a the first value
 * @param b the second value
 * @param delimiters the separator string between the two ratio parts, defaults to `":"`
 * @param swapResult if `true`, the ratio is returned as `b/gcd : a/gcd` instead
 * @return the simplified ratio string, or `null` if computation fails
 *
 * Example:
 * ```
 * getRatio(1920, 1080)                   // "16:9"
 * getRatio(1920, 1080, delimiters = "/") // "16/9"
 * getRatio(1920, 1080, swapResult = true) // "9:16"
 * getRatio(800, 600)                     // "4:3"
 * ```
 */
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

/**
 * Formats an [Int] with comma-separated thousands.
 *
 * Example:
 * ```
 * 1234567.formatDecimalSeparator()  // "1,234,567"
 * 999.formatDecimalSeparator()      // "999"
 * 1000.formatDecimalSeparator()     // "1,000"
 * (-1234).formatDecimalSeparator()  // "-1,234"
 * ```
 */
fun Int.formatDecimalSeparator(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()

/**
 * Formats a [Long] with comma-separated thousands.
 *
 * Example:
 * ```
 * 1234567890L.formatDecimalSeparator()  // "1,234,567,890"
 * 999L.formatDecimalSeparator()         // "999"
 * 10000L.formatDecimalSeparator()       // "10,000"
 * ```
 */
fun Long.formatDecimalSeparator(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()

/**
 * Returns `true` if this [BigDecimal] is numerically equal to zero.
 *
 * Uses [compareTo] instead of [equals] so that `0.00` is considered equal to `0`.
 *
 * Example:
 * ```
 * BigDecimal("0").isZero()    // true
 * BigDecimal("0.00").isZero() // true
 * BigDecimal("1").isZero()    // false
 * ```
 */
fun BigDecimal.isZero() = this.compareTo(BigDecimal.ZERO) == 0

/**
 * Returns `true` if this [BigDecimal] is strictly greater than zero.
 *
 * Example:
 * ```
 * BigDecimal("3.14").isPositive() // true
 * BigDecimal("0").isPositive()    // false
 * BigDecimal("-1").isPositive()   // false
 * ```
 */
fun BigDecimal.isPositive() = this > BigDecimal.ZERO

/**
 * Returns `true` if this [BigDecimal] is strictly less than zero.
 *
 * Example:
 * ```
 * BigDecimal("-1").isNegative()   // true
 * BigDecimal("0").isNegative()    // false
 * BigDecimal("3.14").isNegative() // false
 * ```
 */
fun BigDecimal.isNegative() = this < BigDecimal.ZERO

/**
 * Returns `true` if this [BigDecimal] represents an integer value (no fractional part).
 *
 * Example:
 * ```
 * BigDecimal("5.00").isInteger() // true
 * BigDecimal("5").isInteger()    // true
 * BigDecimal("5.5").isInteger()  // false
 * ```
 */
fun BigDecimal.isInteger(): Boolean {
    return this.stripTrailingZeros().scale() <= 0
}
