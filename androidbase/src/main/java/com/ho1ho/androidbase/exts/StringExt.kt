package com.ho1ho.androidbase.exts

import kotlin.math.ln
import kotlin.math.pow

/**
 * Author: Michael Leo
 * Date: 20-3-13 下午4:44
 */

fun String.reversalEvery2Charts(hasSpace: Boolean = false): String {
    val hex = this.addSpaceEvery2Charts()
    return hex.split(" ").reversed().joinToString(if (hasSpace) " " else "")
}

fun String.addSpaceEvery2Charts(): String {
    val hex = this.replace(" ", "")
    val sb = StringBuilder()
    for (i in 0 until hex.length / 2) {
        sb.append(hex.substring(i * 2, i * 2 + 2))
        sb.append(" ")
    }
    return sb.toString().trim()
}

fun String.hex2ByteArray(): ByteArray {
    val s = this.replace(" ", "")
    val bs = ByteArray(s.length / 2)
    for (i in 0 until s.length / 2) {
        bs[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bs
}

fun String.ascii2ByteArray(hasSpace: Boolean = false): ByteArray {
    val s = if (hasSpace) this else this.replace(" ", "")
    return s.toByteArray(charset("US-ASCII"))
}

fun Long.humanReadableByteCount(si: Boolean = false): String {
    val unit = if (si) 1000 else 1024
    if (this < unit) return "${this}B"
    val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString()
    return "%.1f%s".format(this / unit.toDouble().pow(exp.toDouble()), pre)
}