@file:Suppress("unused")

package com.leovp.kotlin.exts

/**
 * Author: Michael Leo
 * Date: 2023/12/5 10:25
 */

/**
 * Usage:
 * ```
 * enum class Type(val value: Int) {
 *      DOWN(0), UP(1), MOVE(2)
 * }
 * val typeDown = Type::value findBy 0
 * assertEquals(Type.DOWN, typeDown)
 * ```
 * https://www.baeldung.com/kotlin/enum-find-by-value
 */
inline infix fun <reified E : Enum<E>, V> ((E) -> V).findBy(value: V): E? {
    return enumValues<E>().firstOrNull { this(it) == value }
}
