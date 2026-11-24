package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.aac.AacEncoder
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.base.iters.OutputCallback

/**
 * Migration: provide [onFailure] to handle terminal encoder/output failures. The callback can run
 * on a codec callback thread; hand teardown to an external coroutine without waiting here.
 *
 * Author: Michael Leo
 * Date: 20-11-14 上午11:03
 */
class AacEncoderWrapper(
    encoderInfo: AudioEncoderInfo,
    private val outputCallback: OutputCallback,
    onFailure: (Throwable) -> Unit,
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
        },
        errorCallback = onFailure
    ).apply { start() }

    override val failure: Throwable? get() = encoder.terminalFailure

    override fun encode(input: ByteArray) {
        encoder.encode(input)
    }

    override suspend fun releaseAndJoin() {
        encoder.releaseAndJoin()
    }
}
