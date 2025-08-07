@file:Suppress("unused")

package com.leovp.kotlin.exts

/**
 * Author: Michael Leo
 * Date: 2023/12/5 10:25
 */

/**
 * Usage:
 * ```
 * enum class TouchType(val touchVal: Int) {
 *     DOWN(0), UP(1), MOVE(2)
 * }
 *
 * val typeDown = TouchType::touchVal findBy 0
 * assertEquals(TouchType.DOWN, typeDown)
 * ```
 * https://www.baeldung.com/kotlin/enum-find-by-value
 */
inline infix fun <reified E : Enum<E>, V> ((E) -> V).findBy(value: V): E? =
    enumValues<E>().firstOrNull { this(it) == value }
