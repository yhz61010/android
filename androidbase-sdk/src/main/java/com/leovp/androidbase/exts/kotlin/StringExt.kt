package com.leovp.androidbase.exts.kotlin

/**
 * Author: Michael Leo
 * Date: 20-3-13 下午4:44
 */

fun String.toAsciiByteArray() = this.toByteArray(Charsets.US_ASCII)