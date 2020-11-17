package com.leovp.audio.base

import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.decoderWrapper.CompressedPcmDecoderWrapper
import com.leovp.audio.base.iters.AudioDecoderWrapper
import com.leovp.audio.base.iters.OutputCallback

/**
 * Author: Michael Leo
 * Date: 20-11-14 下午4:37
 */
object AudioDecoderManager {
    fun getWrapper(type: AudioType, decoderInfo: AudioDecoderInfo, outputCallback: OutputCallback): AudioDecoderWrapper? {
        return when (type) {
            AudioType.PCM -> null
            AudioType.COMPRESSED_PCM -> CompressedPcmDecoderWrapper(decoderInfo, outputCallback)
            AudioType.AAC -> null
        }
    }
}