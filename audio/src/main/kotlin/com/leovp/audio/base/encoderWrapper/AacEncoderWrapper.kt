package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.aac.AacEncoder
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.base.iters.OutputCallback

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:03
 */
class AacEncoderWrapper(
    encoderInfo: AudioEncoderInfo,
    private val outputCallback: OutputCallback,
) : AudioEncoderWrapper {
    private val encoder = AacEncoder(
        sampleRate = encoderInfo.sampleRate,
        channelCount = encoderInfo.channelCount,
        bitrate = encoderInfo.bitrate,
        audioFormat = encoderInfo.audioFormat,
        callback = object : IEncodeCallback {
            override fun onEncoded(
                encodedBytes: ByteArray,
                isConfig: Boolean,
                isKeyFrame: Boolean
            ) {
                outputCallback.output(encodedBytes, isConfig, isKeyFrame)
            }
        }
    ).apply { start() }

    override fun encode(input: ByteArray) {
        encoder.encode(input)
    }

    override suspend fun releaseAndJoin() {
        encoder.releaseAndJoin()
    }
}
