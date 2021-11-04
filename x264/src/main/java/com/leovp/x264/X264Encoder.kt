package com.leovp.x264

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
class X264Encoder {
    external fun initEncoder(params: X264Params): X264InitResult
    external fun releaseEncoder()
    external fun encodeFrame(frame: ByteArray, colorFormat: Int, pts: Long): X264EncodeResult
    external fun getVersion(): String
    private val ctx: Long = 0

    companion object {
        init {
            System.loadLibrary("libx264-encoder")
            System.loadLibrary("libx264")
        }
    }
}