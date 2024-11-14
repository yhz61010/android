@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.opus

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodecAsynchronous
import com.leovp.bytes.toByteArray
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * https://datatracker.ietf.org/doc/html/rfc6716
 * https://www.rfc-editor.org/rfc/rfc7845#section-5.1
 * https://developer.android.com/reference/android/media/MediaCodec#CSD
 *
 * Author: Michael Leo
 * Date: 2023/4/14 17:10
 */
class OpusDecoder(
    sampleRate: Int,
    channelCount: Int,
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val csd0: ByteArray,
    val csd1: ByteArray,
    val csd2: ByteArray,
    private val callback: IDecodeCallback
) : BaseMediaCodecAsynchronous(
    codecName = MediaFormat.MIMETYPE_AUDIO_OPUS,
    sampleRate = sampleRate,
    channelCount = channelCount,
    audioFormat = audioFormat
) {
    companion object {
        private const val TAG = "OpusDe"
    }

    private val queue = ArrayBlockingQueue<ByteArray>(64)

    private var frameCount: Long = 0

    val queueSize: Int get() = queue.size

    override fun setFormatOptions(format: MediaFormat) {
        // https://developer.android.com/reference/android/media/MediaCodec#CSD
        val csd0BB = ByteBuffer.wrap(csd0)
        val csd1BB = ByteBuffer.wrap(csd1)
        val csd2BB = ByteBuffer.wrap(csd2)
        format.setByteBuffer("csd-0", csd0BB)
        format.setByteBuffer("csd-1", csd1BB)
        format.setByteBuffer("csd-2", csd2BB)
        // format.setInteger(MediaFormat.KEY_COMPLEXITY, 3)
    }

    override fun start() {
        frameCount = 0
        super.start()
    }

    override fun onInputData(inBuf: ByteBuffer): Int {
        return queue.poll()?.let {
            inBuf.put(it)
            it.size
        } ?: 0
    }

    override fun onOutputData(outBuf: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean) {
        frameCount++
        callback.onDecoded(outBuf.toByteArray())
    }

    // timeUsPerFrame = 1_000_000L / sampleRate * 1024
    // presentationTimeUs = totalFrames * timeUsPerFrame
    override fun computePresentationTimeUs(): Long {
        return frameCount * (1_000_000L / sampleRate * 1024)
    }

    fun decode(rawData: ByteArray) {
        queue.offer(rawData)
    }

    override fun stop() {
        queue.clear()
        super.stop()
    }
}
