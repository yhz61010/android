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
class AacEncoderWrapper(encoderInfo: AudioEncoderInfo, private val outputCallback: OutputCallback) : AudioEncoderWrapper {
    private var aacEncoder: AacEncoder = AacEncoder(
        encoderInfo.sampleRate,
        encoderInfo.bitrate,
        encoderInfo.channelCount,
        object : IEncodeCallback {
            override fun onEncoded(encodedBytes: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
                outputCallback.output(encodedBytes)
            }
        }
    ).apply { start() }

    override fun encode(input: ByteArray) {
        aacEncoder.queue.offer(input)
    }

    override fun release() {
        runCatching { aacEncoder.release() }.onFailure { it.printStackTrace() }
    }
}
