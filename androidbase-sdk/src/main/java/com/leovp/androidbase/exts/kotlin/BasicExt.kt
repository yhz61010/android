@file:Suppress("unused")

package com.leovp.androidbase.exts.kotlin

import java.util.*

/**
 * Author: Michael Leo
 * Date: 2021/12/29 13:29
 */

/**
 * Example:
 * ```kotlin
 * val isStringAString = "String".isInstanceOf<String>()
 * val isIntAString = 1.isInstanceOf<String>()
 * ```
 */
inline fun <reified T> Any.isInstanceOf(): Boolean = this is T

inline fun <reified T, reified U> haveSameType(first: T, second: U) = first is U && second is T

inline val <reified T> T.clazz get() = T::class.java

/** Convert Boolean to Int */
val Boolean.toInt get() = if (this) 1 else 0

/** Convert Int value to Boolean value */
val Int.toBoolean: Boolean get() = this != 0

/**
 * Convert Int value to binary value(Int.toString(2))
 * Int.toString(2)              Example: (-12).toString(2) = -1100
 * Integer.toBinaryString(int)  Example: Integer.toBinaryString(-12) = 11111111111111111111111111110100
 */
val Int.toBinary: String get() = Integer.toBinaryString(this)

/**
 * Convert Int value to octal value(Int.toString(8))
 * Int.toString(8)             Example: (-12).toString(16) = -14
 * Integer.toOctalString(int)  Example: Integer.toOctalString(-12) = 37777777764
 */
val Int.toOctal: String get() = Integer.toOctalString(this)

/**
 * Convert Int value to hexadecimal value(Int.toString(16))
 * Int.toString(16)             Example: (-12).toString(16) = -C
 * Integer.toHexString(int)     Example: Integer.toHexString(-12) = FFFFFFF4
 */
val Int.toHexadecimal: String
    get() = Integer.toHexString(this)
        .uppercase(Locale.getDefault())