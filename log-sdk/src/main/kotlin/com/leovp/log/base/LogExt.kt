package com.leovp.log.base

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:39
 */

val Any.ITAG: String
    get() {
        val tag = javaClass.simpleName
        return if (tag.length <= 23) tag else tag.substring(0, 23)
    }
