package com.leovp.androidbase.exts.kotlin

import kotlin.math.ln
import kotlin.math.pow

/**
 * Util for converting Byte to (X)B/(X)iB according to the <IEEE 1541-2002> standard
 */
const val KB = 1000
const val MB = 1000_000
const val GB = 1000_000_000
const val TB = 1000_000_000_000
const val PB = 1000_000_000_000_000
const val EB = 1000_000_000_000_000_000

const val KiB = 1 shl 10
const val MiB = 1 shl 20
const val GiB = 1 shl 30
const val TiB = 1 shl 40
const val PiB = 1 shl 50
const val EiB = 1 shl 60

/**
 * B -> KB
 * Convert Byte to KB (1KB = 1000B).
 */
fun Long.toKB() = this / KB

/**
 * B -> MB
 * Convert Byte to MB (1KB = 1000B).
 */
fun Long.toMB() = this / MB

/**
 * B -> GB
 * Convert Byte to GB (1KB = 1000B).
 */
fun Long.toGB() = this / GB

/**
 * B -> TB
 * Convert Byte to TB (1KB = 1000B).
 */
fun Long.toTB() = this / TB

/**
 * B -> PB
 * Convert Byte to PB (1KB = 1000B).
 */
fun Long.toPB() = this / PB

/**
 * B -> EB
 * Convert Byte to EB (1KB = 1000B).
 */
fun Long.toEB() = this / EB


/**
 * B -> KiB
 * Convert Byte to KiB (1KiB = 1024B).
 */
fun Long.toKiB() = this / KiB

/**
 * B -> MiB
 * Convert Byte to MiB (1KiB = 1024B).
 */
fun Long.toMiB() = this / MiB

/**
 * B -> GiB
 * Convert Byte to GiB (1KiB = 1024B).
 */
fun Long.toGiB() = this / GiB

/**
 * B -> TiB
 * Convert Byte to TiB (1KiB = 1024B).
 */
fun Long.toTiB() = this / TiB

/**
 * B -> PiB
 * Convert Byte to PiB (1KiB = 1024B).
 */
fun Long.toPiB() = this / PiB

/**
 * B -> EiB
 * Convert Byte to EiB (1KiB = 1024B).
 */
fun Long.toEiB() = this / EiB

/**
 * B -> XiB
 * Automatically convert Byte to the appropriate XiB.
 */
fun Long.autoBinary() = when {
    this < 0 -> null
    this >= EiB -> String.format("%.2fEiB", toEiB())
    this >= PiB -> String.format("%.2fPiB", toPiB())
    this >= TiB -> String.format("%.2fTiB", toTiB())
    this >= GiB -> String.format("%.2fGiB", toGiB())
    this >= MiB -> String.format("%.2fMiB", toMiB())
    this >= KiB -> String.format("%.2fKiB", toKiB())
    else -> String.format("%.2fB", this)
}

/**
 * B -> XB
 * Automatically convert Byte to the appropriate XB.
 */
fun Long.autoDecimal() = when {
    this < 0 -> null
    this >= EB -> String.format("%.2fEB", toEB())
    this >= PB -> String.format("%.2fPB", toPB())
    this >= TB -> String.format("%.2fTB", toTB())
    this >= GB -> String.format("%.2fGB", toGB())
    this >= MB -> String.format("%.2fMB", toMB())
    this >= KB -> String.format("%.2fKB", toKB())
    else -> String.format("%.2fB", this)
}

/**
 * B -> XB/XiB
 * Automatically convert Byte to the appropriate XB/XiB.
 * @param decimal true -> B -> XB; false -> B -> XiB
 */
fun Long.auto(decimal: Boolean) = if (decimal) autoDecimal() else autoBinary()

fun Long.humanReadableByteCount(si: Boolean = false): String {
    val unit = if (si) 1000 else 1024
    if (this < unit) return "${this}B"
    val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString()
    return "%.1f%s".format(this / unit.toDouble().pow(exp.toDouble()), pre)
}