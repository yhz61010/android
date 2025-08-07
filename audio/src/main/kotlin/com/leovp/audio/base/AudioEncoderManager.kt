package com.leovp.audio.base

import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.encoderWrapper.AacEncoderWrapper
import com.leovp.audio.base.encoderWrapper.CompressedPcmEncoderWrapper
import com.leovp.audio.base.encoderWrapper.OpusEncoderWrapper
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.OutputCallback

/**
 * Author: Michael Leo
 * Date: 20-11-14 下午3:58
 */
object AudioEncoderManager {
    fun getWrapper(
        type: AudioType,
        encoderInfo: AudioEncoderInfo,
        outputCallback: OutputCallback,
    ): AudioEncoderWrapper? = when (type) {
        AudioType.PCM -> null
        AudioType.COMPRESSED_PCM -> CompressedPcmEncoderWrapper(encoderInfo, outputCallback)
        AudioType.AAC -> AacEncoderWrapper(encoderInfo, outputCallback)
        AudioType.OPUS -> OpusEncoderWrapper(encoderInfo, outputCallback)
    }
}
