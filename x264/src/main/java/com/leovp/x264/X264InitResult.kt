package com.leovp.x264

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
@Keep
data class X264InitResult(val err: Int, val sps: ByteArray, val pps: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as X264InitResult

        if (err != other.err) return false
        if (!sps.contentEquals(other.sps)) return false
        if (!pps.contentEquals(other.pps)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = err
        result = 31 * result + sps.contentHashCode()
        result = 31 * result + pps.contentHashCode()
        return result
    }
}