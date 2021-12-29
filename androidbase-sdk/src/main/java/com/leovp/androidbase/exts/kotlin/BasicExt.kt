@file:Suppress("unused")

package com.leovp.androidbase.exts.kotlin

import android.os.Bundle

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

/**
 * Example
 * ```kotlin
 * val bundle: Bundle = Bundle()
 * bundle.putSerializable("key", "Testing")
 * val value: String? = bundle.getDataOrNull("key")
 * ```
 */
inline fun <reified T> Bundle.getDataOrNull(key: String): T? = getSerializable(key) as? T