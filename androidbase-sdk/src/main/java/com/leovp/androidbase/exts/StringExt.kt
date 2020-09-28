package com.leovp.androidbase.exts

import kotlin.math.ln
import kotlin.math.pow

/**
 * Author: Michael Leo
 * Date: 20-3-13 下午4:44
 */

fun String.hex2ByteArray(hasSpace: Boolean = false): ByteArray {
    val s = if (hasSpace) this.replace(" ", "") else this
    val bs = ByteArray(s.length / 2)
    for (i in 0 until s.length / 2) {
        bs[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bs
}

fun String.ascii2ByteArray(hasSpace: Boolean = false): ByteArray {
    val s = if (hasSpace) this.replace(" ", "") else this
    return s.toByteArray(charset("US-ASCII"))
}

fun Long.humanReadableByteCount(si: Boolean = false): String {
    val unit = if (si) 1000 else 1024
    if (this < unit) return "${this}B"
    val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString()
    return "%.1f%s".format(this / unit.toDouble().pow(exp.toDouble()), pre)
}