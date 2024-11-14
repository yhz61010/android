@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.aac

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodecSynchronous
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec.Companion.AAC_PROFILE_LC
import com.leovp.bytes.toByteArray
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * If I use asynchronous MediaCodec, in my phone(HuaWei Honor V20) and Samsung Galaxy S9+,
 * it will not play sound due to MediaCodec state error.
 *
 * Author: Michael Leo
 * Date: 20-8-20 下午5:18
 */
class AacDecoder(
    sampleRate: Int,
    channelCount: Int,
    audioFormat: Int,
    private val csd0: ByteArray,
    private val callback: IDecodeCallback
) : BaseMediaCodecSynchronous(
    codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
    sampleRate = sampleRate,
    channelCount = channelCount,
    audioFormat = audioFormat
) {
    companion object {
        private const val TAG = "AacDe"
    }

    private val queue = ArrayBlockingQueue<ByteArray>(64)

    private var frameCount: Long = 0

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
        LogContext.log.w(TAG, "setFormatOptions csd0[${csd0.size}]=HEX[${csd0.toHexString()}]")
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE_LC)
        // Set ADTS decoder information.
        format.setInteger(MediaFormat.KEY_IS_ADTS, 1)
        // https://developer.android.com/reference/android/media/MediaCodec#CSD
        val csd0BB = ByteBuffer.wrap(csd0)
        format.setByteBuffer("csd-0", csd0BB)
    }

    override fun start() {
        frameCount = 0
        super.start()
    }

    override fun onInputData(inBuf: ByteBuffer): Int {
        return queue.take().let {
            inBuf.put(it)
            it.size
        }
    }

    override fun onOutputData(outBuf: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean) {
        // LogContext.log.e(TAG, "--->>> onOutputData[${outData.remaining()}]")
        frameCount++
        callback.onDecoded(outBuf.toByteArray())
    }

    fun decode(aacData: ByteArray) {
        // LogContext.log.e(TAG, "--->>> decode[${aacData.size}] queue[${queue.size}]")
        queue.offer(aacData)
    }

    // timeUsPerFrame = 1_000_000L / sampleRate * 1024
    // presentationTimeUs = totalFrames * timeUsPerFrame
    override fun computePresentationTimeUs(): Long {
        return frameCount * (1_000_000L / sampleRate * 1024)
    }

    override fun stop() {
        queue.clear()
        super.stop()
    }
}
