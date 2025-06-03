package com.leovp.audio.mediacodec.bean

/**
 * Author: Michael Leo
 * Date: 2023/4/26 14:48
 */
data class OpusCsd(val csd0: ByteArray, val csd1: ByteArray, val csd2: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpusCsd

        if (!csd0.contentEquals(other.csd0)) return false
        if (!csd1.contentEquals(other.csd1)) return false
        if (!csd2.contentEquals(other.csd2)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = csd0.contentHashCode()
        result = 31 * result + csd1.contentHashCode()
        result = 31 * result + csd2.contentHashCode()
        return result
    }
}
