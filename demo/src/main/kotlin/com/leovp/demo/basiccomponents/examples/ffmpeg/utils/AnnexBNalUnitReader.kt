package com.leovp.demo.basiccomponents.examples.ffmpeg.utils

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream

/** Sequentially reads Annex-B NAL units while preserving their three- or four-byte start codes. */
internal class AnnexBNalUnitReader(input: InputStream) : Closeable {
    private val input = if (input is BufferedInputStream) input else BufferedInputStream(input)
    private var pendingStartCode: ByteArray? = null
    private var reachedEndOfInput = false

    fun nextNalUnit(): ByteArray? {
        if (reachedEndOfInput) return null
        val startCode = pendingStartCode ?: findStartCode() ?: return null
        pendingStartCode = null

        val output = ByteArrayOutputStream()
        output.write(startCode, 0, startCode.size)
        var pendingZeros = 0
        while (true) {
            val value = input.read()
            if (value == -1) {
                reachedEndOfInput = true
                repeat(pendingZeros) { output.write(0) }
                return output.toByteArray()
            }
            if (value == 0) {
                pendingZeros++
                continue
            }
            if (value == 1 && pendingZeros >= MIN_START_CODE_ZERO_COUNT) {
                val startCodeZeroCount = pendingZeros.coerceAtMost(MAX_START_CODE_ZERO_COUNT)
                repeat(pendingZeros - startCodeZeroCount) { output.write(0) }
                pendingStartCode = ByteArray(startCodeZeroCount + 1).also { it[it.lastIndex] = 1 }
                return output.toByteArray()
            }
            repeat(pendingZeros) { output.write(0) }
            pendingZeros = 0
            output.write(value)
        }
    }

    /**
     * Joins leading non-picture NAL units with the next picture NAL unit selected by
     * [isEndingNalUnit].
     * An incomplete trailing group is discarded because it cannot produce a decoded frame.
     */
    fun nextNalUnitGroupEndingWith(isEndingNalUnit: (ByteArray) -> Boolean): AnnexBNalUnitGroup? {
        val output = ByteArrayOutputStream()
        while (true) {
            val nalUnit = nextNalUnit() ?: return null
            output.write(nalUnit, 0, nalUnit.size)
            if (isEndingNalUnit(nalUnit)) {
                return AnnexBNalUnitGroup(output.toByteArray(), nalUnit)
            }
        }
    }

    override fun close() = input.close()

    private fun findStartCode(): ByteArray? {
        var zeroCount = 0
        while (true) {
            when (val value = input.read()) {
                -1 -> {
                    reachedEndOfInput = true
                    return null
                }

                0 -> zeroCount++
                1 -> {
                    if (zeroCount >= MIN_START_CODE_ZERO_COUNT) {
                        val startCodeZeroCount = zeroCount.coerceAtMost(MAX_START_CODE_ZERO_COUNT)
                        return ByteArray(startCodeZeroCount + 1).also { it[it.lastIndex] = 1 }
                    }
                    zeroCount = 0
                }

                else -> zeroCount = 0
            }
        }
    }

    private companion object {
        const val MIN_START_CODE_ZERO_COUNT = 2
        const val MAX_START_CODE_ZERO_COUNT = 3
    }
}

internal data class AnnexBNalUnitGroup(val bytes: ByteArray, val endingNalUnit: ByteArray)

internal fun h264NalUnitType(nalUnit: ByteArray): Int =
    nalHeaderOffset(nalUnit)?.let { nalUnit[it].toInt() and 0x1F } ?: -1

internal fun h265NalUnitType(nalUnit: ByteArray): Int =
    nalHeaderOffset(nalUnit)?.let { (nalUnit[it].toInt() ushr 1) and 0x3F } ?: -1

private fun nalHeaderOffset(nalUnit: ByteArray): Int? = when {
    nalUnit.size >= 5 &&
        nalUnit[0] == 0.toByte() &&
        nalUnit[1] == 0.toByte() &&
        nalUnit[2] == 0.toByte() &&
        nalUnit[3] == 1.toByte() -> 4

    nalUnit.size >= 4 &&
        nalUnit[0] == 0.toByte() &&
        nalUnit[1] == 0.toByte() &&
        nalUnit[2] == 1.toByte() -> 3

    else -> null
}
