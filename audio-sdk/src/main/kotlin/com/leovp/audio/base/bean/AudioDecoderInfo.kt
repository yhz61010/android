package com.leovp.audio.base.bean

import android.media.AudioFormat
import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 20-8-13 下午7:21
 *
 * Example:
 * ```
 * sampleRate: 44100Hz
 * channelConfig:
 *              AudioFormat.CHANNEL_OUT_STEREO(12=0xc)
 *              AudioFormat.CHANNEL_OUT_MONO (0x4)
 * channelCount: 1
 * audioFormat(bit depth):
 *              AudioFormat.ENCODING_PCM_16BIT(0x2)
 *              AudioFormat.ENCODING_PCM_8BIT(0x3)
 *              AudioFormat.ENCODING_PCM_FLOAT(0x4)
 * ```
 */
@Keep
data class AudioDecoderInfo(
    val sampleRate: Int,
    val channelConfig: Int,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
) {
    val channelCount: Int = when (channelConfig) {
        AudioFormat.CHANNEL_OUT_MONO -> 1
        AudioFormat.CHANNEL_OUT_STEREO -> 2
        else -> throw kotlin.IllegalArgumentException("Illegal channelConfig value=$channelConfig")
    }
}
