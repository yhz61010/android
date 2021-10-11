package com.leovp.screenshot.util

fun fail(message: String): Nothing {
    throw IllegalArgumentException(message)
}

fun exception(message: String): Nothing {
    throw Exception(message)
}