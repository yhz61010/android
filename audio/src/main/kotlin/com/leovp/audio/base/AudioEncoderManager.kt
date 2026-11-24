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
    /**
     * Migration: callers must provide [onFailure] for terminal asynchronous encoding failures.
     * The callback must return promptly; teardown belongs in an external coroutine. PCM and
     * compressed PCM still report synchronous output failures directly to the caller.
     */
    fun getWrapper(
        type: AudioType,
        encoderInfo: AudioEncoderInfo,
        outputCallback: OutputCallback,
        onFailure: (Throwable) -> Unit,
    ): AudioEncoderWrapper? = when (type) {
        AudioType.PCM -> null
        AudioType.COMPRESSED_PCM -> CompressedPcmEncoderWrapper(encoderInfo, outputCallback)
        AudioType.AAC -> AacEncoderWrapper(encoderInfo, outputCallback, onFailure)
        AudioType.OPUS -> OpusEncoderWrapper(encoderInfo, outputCallback, onFailure)
    }
}
