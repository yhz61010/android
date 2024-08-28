@file:Suppress("unused")

package com.leovp.kotlin.exts

import kotlin.reflect.KClass

// fun fail(message: String): Nothing {
//     throw IllegalArgumentException(message)
// }
//
// fun exception(message: String): Nothing {
//     throw Exception(message)
// }

/**
 * Usage:
 * ```
 * multiCatch(
 *     runBlock = {
 *     },
 *     exceptions = arrayOf(
 *         SerializationException::class,
 *         IllegalArgumentException::class
 *     ),
 *     // nullable argument
 *     catchBlock = {
 *     },
 *     // nullable argument
 *     uncaughtBlock = {
 *     }
 * )
 * ```
 */
inline fun multiCatch(
    runBlock: () -> Unit,
    vararg exceptions: KClass<out Throwable>,
    noinline catchBlock: ((Throwable) -> Unit)? = null,
    noinline uncaughtBlock: ((Throwable) -> Unit)? = null,
) {
    try {
        runBlock()
    } catch (e: Throwable) {
        if (exceptions.any { it.isInstance(e) }) {
            catchBlock?.invoke(e)
        } else {
            // Re-throw if not in the specified exceptions
            uncaughtBlock?.invoke(e) ?: throw e
        }
    }
}
