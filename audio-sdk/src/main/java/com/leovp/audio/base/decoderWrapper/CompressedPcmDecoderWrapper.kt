package com.leovp.audio.base.decoderWrapper

import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.AudioDecoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.lib_compress.decompress

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:03
 */
class CompressedPcmDecoderWrapper(@Suppress("UNUSED_PARAMETER") decoderInfo: AudioDecoderInfo, private val outputCallback: OutputCallback) : AudioDecoderWrapper {
    override fun decode(input: ByteArray) {
        outputCallback.output(input.decompress())
    }

    override fun release() {
    }
}