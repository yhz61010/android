package com.leovp.kotlin.exts

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
 * B -> kB
 * Convert Byte to KB (1kB = 1000B).
 */
fun Long.toKB() = this * 1.0F / KB

/**
 * B -> MB
 * Convert Byte to MB (1kB = 1000B).
 */
fun Long.toMB() = this * 1.0F / MB

/**
 * B -> GB
 * Convert Byte to GB (1kB = 1000B).
 */
fun Long.toGB() = this * 1.0F / GB

/**
 * B -> TB
 * Convert Byte to TB (1kB = 1000B).
 */
fun Long.toTB() = this * 1.0F / TB

/**
 * B -> PB
 * Convert Byte to PB (1kB = 1000B).
 */
fun Long.toPB() = this * 1.0F / PB

/**
 * B -> EB
 * Convert Byte to EB (1kB = 1000B).
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
 * Automatically convert Byte to the appropriate XiB (1KiB = 1024B).
 */
fun Long.autoByteInBinary(precision: Int = 2): String = when {
    this < 0 -> "NA"
    this >= EiB -> String.format("%.${precision}fEiB", toEiB())
    this >= PiB -> String.format("%.${precision}fPiB", toPiB())
    this >= TiB -> String.format("%.${precision}fTiB", toTiB())
    this >= GiB -> String.format("%.${precision}fGiB", toGiB())
    this >= MiB -> String.format("%.${precision}fMiB", toMiB())
    this >= KiB -> String.format("%.${precision}fKiB", toKiB())
    else -> "${this}B"
}

/**
 * B -> XB
 * Automatically convert Byte to the appropriate XB (1kB = 1000B).
 */
fun Long.autoByteInDecimal(precision: Int = 2): String = when {
    this < 0 -> "NA"
    this >= EB -> String.format("%.${precision}fEB", toEB())
    this >= PB -> String.format("%.${precision}fPB", toPB())
    this >= TB -> String.format("%.${precision}fTB", toTB())
    this >= GB -> String.format("%.${precision}fGB", toGB())
    this >= MB -> String.format("%.${precision}fMB", toMB())
    this >= KB -> String.format("%.${precision}fkB", toKB())
    else -> "${this}B"
}

/**
 * B -> XB/XiB
 * Automatically convert Byte to the appropriate XB/XiB.
 *
 * SI(The International System of Units): Base quantity is 1000, units are B, kB, MB, GB, TB, PB, EB, ZB, YB.
 * IEC(The International Electrotechnical Commission): Base quantity is 1024, units are B, KiB, MiB, GiB, TiB, PiB, EiB, ZiB, YiB.
 *
 * @param si true -> B -> XB; false -> B -> XiB
 */
fun Long.autoFormatByte(si: Boolean = false, precision: Int = 2): String =
    if (si) autoByteInDecimal(precision) else autoByteInBinary(precision)

// Formatter.formatShortFileSize(ctx, bytes)
/**
 * SI(The International System of Units): Base quantity is 1000, units are B, kB, MB, GB, TB, PB, EB, ZB, YB.
 * IEC(The International Electrotechnical Commission): Base quantity is 1024, units are B, KiB, MiB, GiB, TiB, PiB, EiB, ZiB, YiB.
 */
fun Long.humanReadableByteCount(si: Boolean = false, precision: Int = 2): String {
    if (this < 0) return "NA"
    val base = if (si) 1000 else 1024
    if (this < base) return "${this}B"
    val exp = (ln(this.toDouble()) / ln(base.toDouble())).toInt()
    val pre = (if (si) "kMGTPEZY" else "KMGTPEZY")[exp - 1].toString()
    return "%.${precision}f%s%s".format(this / base.toDouble().pow(exp.toDouble()), pre, if (si) "B" else "iB")
}

fun Long.outputFormatByte(): String = "${autoByteInBinary()}(${autoByteInDecimal()})"
