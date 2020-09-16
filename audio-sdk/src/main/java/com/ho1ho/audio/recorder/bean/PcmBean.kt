package com.ho1ho.audio.recorder.bean

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 20-2-26 上午11:27
 *
 * @param pcm ENCODING_PCM_16BIT pcm data array
 */
@Keep
data class PcmBean(val pcm: ShortArray, val readSize: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PcmBean

        if (!pcm.contentEquals(other.pcm)) return false
        if (readSize != other.readSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pcm.contentHashCode()
        result = 31 * result + readSize
        return result
    }
}