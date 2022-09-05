@file:Suppress("unused")

package com.leovp.kotlin.exts

import kotlin.reflect.KClass

fun fail(message: String): Nothing {
    throw IllegalArgumentException(message)
}

fun exception(message: String): Nothing {
    throw Exception(message)
}

inline fun multiCatch(runBlock: () -> Unit, vararg exceptions: KClass<out Throwable>, catchBlock: (Throwable) -> Unit) {
    try {
        runBlock()
    } catch (e: Throwable) {
        if (e::class in exceptions) catchBlock(e) else throw e
    }
}
