package com.leovp.androidbase.exts.kotlin

fun fail(message: String): Nothing {
    throw IllegalArgumentException(message)
}

fun exception(message: String): Nothing {
    throw Exception(message)
}