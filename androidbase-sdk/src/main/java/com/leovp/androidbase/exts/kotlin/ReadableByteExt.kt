package com.leovp.androidbase.exts.kotlin

import kotlin.math.ln
import kotlin.math.pow

/**
 * Util for converting Byte to (X)B/(X)iB according to the <IEEE 1541-2002> standard
 */
const val KB: Long = 1000
const val MB: Long = 1000_000
const val GB: Long = 1000_000_000
const val TB: Long = 1000_000_000_000
const val PB: Long = 1000_000_000_000_000
const val EB: Long = 1000_000_000_000_000_000

const val KiB: Long = 1L shl 10
const val MiB: Long = 1L shl 20
const val GiB: Long = 1L shl 30
const val TiB: Long = 1L shl 40
const val PiB: Long = 1L shl 50
const val EiB: Long = 1L shl 60

/**
 * B -> KB
 * Convert Byte to KB (1KB = 1000B).
 */
fun Long.toKB() = this * 1.0F / KB

/**
 * B -> MB
 * Convert Byte to MB (1KB = 1000B).
 */
fun Long.toMB() = this * 1.0F / MB

/**
 * B -> GB
 * Convert Byte to GB (1KB = 1000B).
 */
fun Long.toGB() = this * 1.0F / GB

/**
 * B -> TB
 * Convert Byte to TB (1KB = 1000B).
 */
fun Long.toTB() = this * 1.0F / TB

/**
 * B -> PB
 * Convert Byte to PB (1KB = 1000B).
 */
fun Long.toPB() = this * 1.0F / PB

/**
 * B -> EB
 * Convert Byte to EB (1KB = 1000B).
 */
fun Long.toEB() = this * 1.0F / EB


/**
 * B -> KiB
 * Convert Byte to KiB (1KiB = 1024B).
 */
fun Long.toKiB() = this * 1.0F / KiB

/**
 * B -> MiB
 * Convert Byte to MiB (1KiB = 1024B).
 */
fun Long.toMiB() = this * 1.0F / MiB

/**
 * B -> GiB
 * Convert Byte to GiB (1KiB = 1024B).
 */
fun Long.toGiB() = this * 1.0F / GiB

/**
 * B -> TiB
 * Convert Byte to TiB (1KiB = 1024B).
 */
fun Long.toTiB() = this * 1.0F / TiB

/**
 * B -> PiB
 * Convert Byte to PiB (1KiB = 1024B).
 */
fun Long.toPiB() = this * 1.0F / PiB

/**
 * B -> EiB
 * Convert Byte to EiB (1KiB = 1024B).
 */
fun Long.toEiB() = this * 1.0F / EiB

/**
 * B -> XiB
 * Automatically convert Byte to the appropriate XiB.
 */
fun Long.autoByteInBinary() = when {
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
fun Long.autoByteInDecimal() = when {
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
fun Long.autoFormatByte(decimal: Boolean) = if (decimal) autoByteInDecimal() else autoByteInBinary()

// Formatter.formatShortFileSize(ctx, bytes)
fun Long.humanReadableByteCount(si: Boolean = false): String {
    val unit = if (si) 1000 else 1024
    if (this < unit) return "${this}B"
    val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString()
    return "%.1f%s".format(this / unit.toDouble().pow(exp.toDouble()), pre)
}

fun Long.outputFormatByte() = "${autoByteInBinary()}(${autoByteInDecimal()})"