package com.ho1ho.audiorecord.bean

import android.media.AudioFormat

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:00
 */
class AudioInfoBean {
    var sampleRate = 0
    var bitrate = 0
    val channelConfig: Int
        get() = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
    var channelCount = 0
    var audioFormat = 0

}