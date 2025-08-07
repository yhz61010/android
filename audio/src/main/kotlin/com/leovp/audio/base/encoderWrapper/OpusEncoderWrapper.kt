package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.audio.opus.OpusEncoder

/**
 * Author: Michael Leo
 * Date: 2023/4/14 17:10
 */
class OpusEncoderWrapper(encoderInfo: AudioEncoderInfo, private val outputCallback: OutputCallback,) :
    AudioEncoderWrapper {

    private val encoder = OpusEncoder(
        sampleRate = encoderInfo.sampleRate,
        channelCount = encoderInfo.channelCount,
        bitrate = encoderInfo.bitrate,
        audioFormat = encoderInfo.audioFormat,
        callback = object : IEncodeCallback {
            override fun onEncoded(encodedBytes: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
                outputCallback.output(encodedBytes, isConfig, isKeyFrame)
            }
        }
    ).apply { start() }

    override fun encode(input: ByteArray) {
        encoder.queue.offer(input)
    }

    override fun release() {
        runCatching { encoder.release() }.onFailure { it.printStackTrace() }
    }
}
