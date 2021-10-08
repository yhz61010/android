package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.util.compress

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:03
 */
class CompressedPcmEncoderWrapper(encoderInfo: AudioEncoderInfo, private val outputCallback: OutputCallback) : AudioEncoderWrapper {
    override fun encode(input: ByteArray) {
        outputCallback.output(input.compress())
    }

    override fun release() {
    }
}