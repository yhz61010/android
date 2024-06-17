package com.leovp.audio.mediacodec.iter

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2023/4/25 16:37
 */
interface IAudioMediaCodec {
    companion object {
        const val OPUS_AOPUSHDR = 0x524448_5355504F41L // "AOPUSHDR" in ASCII (little-endian)
        const val OPUS_AOPUSDLY = 0x594c44_5355504F41L // "AOPUSDLY" in ASCII (little-endian)
        const val OPUS_AOPUSPRL = 0x4c5250_5355504F41L // "AOPUSPRL" in ASCII (little-endian)

        const val AAC_PROFILE_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    // ==========
    /**
     * Fill [inBuf] with valid data.
     *
     * @return The length of bytes of valid input data. This value must be greater than equal to 0.
     */
    fun onInputData(inBuf: ByteBuffer): Int

    fun onOutputData(outBuf: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean)

    fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}

    /**
     * Calculate PTS.
     * Actually, it doesn't make any error if you return 0 directly.
     * However, it's recommended to set it with a correct value.
     *
     * For audio encoder, the formula is:
     * ```
     * presentationTimeUs = 1_000_000L * (totalPcmBytes / (bitPerSample / 8)) / sampleRate / channelCount
     * ```
     * - totalPcmBytes: The total bytes of pcm data.
     * - bitPerSample: Common values are 8bit, 16bit, 24bit, 32bit.
     *
     * For audio decoder, the formula is :
     * ```
     * framesPerSec   = sampleRate / 1024                   (Unit: frames/sec)
     *
     * timeUsPerFrame = 1 / framesPerSec * 1_000_000        (Unit: us/frame)
     * timeUsPerFrame = 1_000_000 / (sampleRate / 1024)     (Unit: us/frame)
     * timeUsPerFrame = 1_000_000L / sampleRate * 1024      (Unit: us/frame)
     *
     * For example:
     * timeUsPerFrame = 1_000_000L / 44100 * 1024 = 23,219.9546485261 (23.22ms/frame)
     * timeUsPerFrame = 1_000_000L / 48000 * 1024 = 21,333.3333333333 (21.33ms/frame)
     *
     * presentationTimeUs = totalFrames * timeUsPerFrame                 (Unit: us)
     * presentationTimeUs = totalFrames * 1_000_000L / sampleRate * 1024 (Unit: us)
     * ```
     * - For AAC-LC, HE-AAC, AAC-SSR, each frame contains 1024 samples.
     * - For AAC-LD, each frame contains 512 samples.
     *
     * https://avmedia.0voice.com/?id=478
     *
     * @return The calculated presentation time in microseconds or -1 if no more samples are available.
     */
    fun computePresentationTimeUs(): Long

    fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {}

    // ==========
    fun onEndOfStream() {}
}
