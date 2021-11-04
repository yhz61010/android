package com.leovp.x264

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
@Keep
class X264Params {
    var width = 1280
    var height = 720
    var bitrate = 500
    var fps = 24
    var gop = 48
    var profile = "baseline"
    var preset = "ultrafast"

    companion object {
        const val CSP_I420 = 0x0001 // yuv 4:2:0 planar
        const val CSP_YV12 = 0x0002 // yvu 4:2:0 planar
        const val CSP_NV12 = 0x0003 // yuv 4:2:0, with one y plane and one packed u+v
        const val CSP_NV21 = 0x0004 // yuv 4:2:0, with one y plane and one packed v+u
    }
}