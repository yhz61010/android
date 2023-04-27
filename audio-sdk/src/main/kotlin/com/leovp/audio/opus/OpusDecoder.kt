package com.leovp.audio.opus

import android.media.MediaFormat
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodec
import com.leovp.bytes.toByteArray
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * Author: Michael Leo
 * Date: 2023/4/14 17:10
 */
@Suppress("unused")
class OpusDecoder(sampleRate: Int,
    channelCount: Int,
    private val csd0: ByteArray,
    private val csd1: ByteArray,
    private val csd2: ByteArray,
    private val callback: IDecodeCallback) : BaseMediaCodec(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount) {
    companion object {
        private const val TAG = "OpusDe"
    }

    private val queue = ArrayBlockingQueue<ByteArray>(64)

    fun getCsd0(): ByteArray = csd0
    fun getCsd1(): ByteArray = csd1
    fun getCsd2(): ByteArray = csd2

    override fun setFormatOptions(format: MediaFormat) {
        // https://developer.android.com/reference/android/media/MediaCodec#CSD
        val csd0BB = ByteBuffer.wrap(csd0)
        val csd1BB = ByteBuffer.wrap(csd1)
        val csd2BB = ByteBuffer.wrap(csd2)
        format.setByteBuffer("csd-0", csd0BB)
        format.setByteBuffer("csd-1", csd1BB)
        format.setByteBuffer("csd-2", csd2BB)
    }

    override fun onInputData(): ByteArray? {
        return queue.poll()
    }

    override fun onOutputData(outData: ByteBuffer, isConfig: Boolean, isKeyFrame: Boolean) {
        callback.onDecoded(outData.toByteArray())
    }

    fun decode(rawData: ByteArray) {
        queue.offer(rawData)
    }

    override fun stop() {
        queue.clear()
        super.stop()
    }
}
