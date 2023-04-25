package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.audio.opus.OpusEncoder

/**
  * Author: Michael Leo
  * Date: 2023/4/14 17:10
  */
class OpusEncoderWrapper(encoderInfo: AudioEncoderInfo, private val outputCallback: OutputCallback) : AudioEncoderWrapper {
    private var opusEncoder: OpusEncoder = OpusEncoder(
        encoderInfo.sampleRate,
        encoderInfo.bitrate,
        encoderInfo.channelCount,
        object : OpusEncoder.OpusEncodeCallback {
            override fun onEncoded(aacData: ByteArray) {
                outputCallback.output(aacData)
            }
        }
    ).apply { start() }

    override fun encode(input: ByteArray) {
        opusEncoder.queue.offer(input)
    }

    override fun release() {
        runCatching { opusEncoder.release() }.onFailure { it.printStackTrace() }
    }
}
