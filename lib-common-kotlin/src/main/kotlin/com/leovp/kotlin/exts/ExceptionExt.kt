package com.leovp.kotlin.exts

fun fail(message: String): Nothing {
    throw IllegalArgumentException(message)
}

fun exception(message: String): Nothing {
    throw Exception(message)
}
