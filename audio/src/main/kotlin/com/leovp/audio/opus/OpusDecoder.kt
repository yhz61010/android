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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * https://datatracker.ietf.org/doc/html/rfc6716
 * https://www.rfc-editor.org/rfc/rfc7845#section-5.1
 * https://developer.android.com/reference/android/media/MediaCodec#CSD
 *
 * Author: Michael Leo
 * Date: 2023/4/14 17:10
 */
@Suppress("LongParameterList")
class OpusDecoder(
    sampleRate: Int,
    channelCount: Int,
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val csd0: ByteArray,
    val csd1: ByteArray,
    val csd2: ByteArray,
    private val callback: IDecodeCallback,
    private val endCallback: () -> Unit = {},
    private val errorCallback: (Throwable) -> Unit = {},
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
    private val eosMarker = ByteArray(0)
    private val eosRequested = AtomicBoolean(false)

    private var inputFrameCount: Long = 0
    private var currentInputPtsUs: Long = 0
    private var currentInputIsEos = false

    val queueSize: Int get() = queue.size
    val isAcceptingInput: Boolean get() = isRunning && !eosRequested.get()

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

    override fun onBeforeCodecStart() {
        inputFrameCount = 0
        currentInputPtsUs = 0
        currentInputIsEos = false
        eosRequested.set(false)
    }

    override fun onInputData(inBuf: ByteBuffer): Int {
        val input = queue.poll()
        currentInputIsEos = input === eosMarker
        if (input == null || currentInputIsEos) return 0
        inBuf.put(input)
        currentInputPtsUs = inputFrameCount++ * 1_000_000L * 1024 / sampleRate
        return input.size
    }

    override fun onOutputData(
        outBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        isConfig: Boolean,
        isKeyFrame: Boolean
    ) {
        callback.onDecoded(outBuf.toByteArray())
    }

    // timeUsPerFrame = 1_000_000L / sampleRate * 1024
    // presentationTimeUs = totalFrames * timeUsPerFrame
    override fun computePresentationTimeUs(): Long = if (currentInputIsEos) {
        -1
    } else {
        currentInputPtsUs
    }

    fun decode(rawData: ByteArray): Boolean =
        rawData.isNotEmpty() && isRunning && !eosRequested.get() && queue.offer(rawData)

    /** Queues input EOS after all previously accepted OPUS frames. Safe to retry on `false`. */
    fun signalEndOfStream(): Boolean {
        if (!isRunning || !eosRequested.compareAndSet(false, true)) return eosRequested.get()
        if (queue.offer(eosMarker)) return true
        eosRequested.set(false)
        return false
    }

    override fun onEndOfStream() {
        endCallback.invoke()
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        errorCallback.invoke(e)
    }

    override fun onCodecReleased() {
        queue.clear()
        currentInputIsEos = false
        inputFrameCount = 0
        currentInputPtsUs = 0
        eosRequested.set(false)
    }
}
