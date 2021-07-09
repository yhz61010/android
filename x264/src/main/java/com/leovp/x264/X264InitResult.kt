package com.leovp.ffmpeg.audio.x264

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
data class X264InitResult(val err: Int, val sps: ByteArray, val pps: ByteArray)