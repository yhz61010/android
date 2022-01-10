@file:Suppress("unused")

package com.leovp.androidbase.exts.kotlin

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