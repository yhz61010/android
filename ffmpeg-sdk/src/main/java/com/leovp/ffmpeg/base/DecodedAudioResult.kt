package com.leovp.ffmpeg.base

/**
 * Author: Michael Leo
 * Date: 2021/6/18 11:23
 */
data class DecodedAudioResult(val leftChannelData: ByteArray, val rightChannelData: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecodedAudioResult

        if (!leftChannelData.contentEquals(other.leftChannelData)) return false
        if (!rightChannelData.contentEquals(other.rightChannelData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = leftChannelData.contentHashCode()
        result = 31 * result + rightChannelData.contentHashCode()
        return result
    }
}