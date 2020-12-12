package com.leovp.androidbase.exts.kotlin

/**
 * Author: Michael Leo
 * Date: 20-12-12 下午2:49
 */
fun sleep(millis: Long) {
    runCatching { Thread.sleep(millis) }.onFailure { it.printStackTrace() }
}