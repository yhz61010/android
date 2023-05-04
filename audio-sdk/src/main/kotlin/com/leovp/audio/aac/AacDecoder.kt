@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.aac

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodecSynchronous
import com.leovp.bytes.toByteArray
import com.leovp.bytes.toHexStringLE
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午5:18
 */
class AacDecoder(
    sampleRate: Int,
    channelCount: Int,
    private val csd0: ByteArray,
    private val callback: IDecodeCallback) : BaseMediaCodecSynchronous(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount) {
    companion object {
        private const val TAG = "AacDe"
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    private val queue = ArrayBlockingQueue<ByteArray>(64)

    val queueSize: Int get() = queue.size

    // ByteBuffer key
    // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
    // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
    // Convert them to binary value: 0b10, 0b100, 0b1
    // According to AAC required, convert theirs values to binary bits:
    // 00010 0100 0001 000
    // The corresponding hex value：
    // 0001 0010 0000 1000
    // So the csd_0 value is 0x12,0x08
    // https://developer.android.com/reference/android/media/MediaCodec
    // AAC CSD: Decoder-specific information from ESDS
    //
    // ByteBuffer key
    // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
    // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
    // Convert them to binary value: 0b10, 0b100, 0b1
    // According to AAC required, convert theirs values to binary bits:
    // 00010 0100 0001 000
    // The corresponding hex value：
    // 0001 0010 0000 1000
    // So the csd_0 value is 0x12,0x08
    // https://developer.android.com/reference/android/media/MediaCodec
    // AAC CSD: Decoder-specific information from ESDS
    override fun setFormatOptions(format: MediaFormat) {
        LogContext.log.w(TAG, "setFormatOptions csd0[${csd0.size}]=HEX[${csd0.toHexStringLE()}]")
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
        // Set ADTS decoder information.
        format.setInteger(MediaFormat.KEY_IS_ADTS, 1)
        // https://developer.android.com/reference/android/media/MediaCodec#CSD
        val csd0BB = ByteBuffer.wrap(csd0)
        format.setByteBuffer("csd-0", csd0BB)
    }

    override fun onInputData(): ByteArray? = queue.poll()

    override fun onOutputData(outData: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean) {
        // LogContext.log.e(TAG, "--->>> onOutputData[${outData.remaining()}]")
        callback.onDecoded(outData.toByteArray())
    }

    /**
     * If I use asynchronous MediaCodec, most of time in my phone(HuaWei Honor V20), it will not play sound due to MediaCodec state error.
     */
    fun decode(rawData: ByteArray) {
        // LogContext.log.e(TAG, "--->>> decode[${rawData.size}] queue[${queue.size}]")
        queue.offer(rawData)
    }

    override fun stop() {
        queue.clear()
        super.stop()
    }
}
