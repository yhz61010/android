package com.leovp.audio.opus

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.bytes.toHexStringLE
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2023/4/14 17:10
 */
@Suppress("unused")
class OpusDecoder(private val audioDecoderInfo: AudioDecoderInfo, private val callback: OpusDecodeCallback) {
    companion object {
        private const val TAG = "OpusDecoder"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    //    private var outputFormat: MediaFormat? = null
    private var frameCount = AtomicLong(0)
    private var audioDecoder: MediaCodec? = null

    @Suppress("WeakerAccess")
    var csd0: ByteArray? = null
        private set
    var csd1: ByteArray? = null
        private set
    var csd2: ByteArray? = null
        private set

    /**
     * https://developer.android.com/reference/android/media/MediaCodec#CSD
     */
    @Suppress("unused")
    fun initAudioDecoder(csd0: ByteArray, csd1: ByteArray, csd2: ByteArray) {
        runCatching {
            this.csd0 = csd0
            this.csd1 = csd1
            this.csd2 = csd2
            LogContext.log.i(TAG, "initAudioDecoder: $audioDecoderInfo")
            LogContext.log.i(TAG, "CSD0[${csd0.size}]=${csd0.toHexStringLE()}")
            LogContext.log.i(TAG, "CSD1[${csd1.size}]=${csd1.toHexStringLE()}")
            LogContext.log.i(TAG, "CSD2[${csd2.size}]=${csd2.toHexStringLE()}")
            val csd0BB = ByteBuffer.wrap(csd0)
            val csd1BB = ByteBuffer.wrap(csd1)
            val csd2BB = ByteBuffer.wrap(csd2)
            val mediaFormat =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, audioDecoderInfo.sampleRate, audioDecoderInfo.channelCount)
                    .apply { // Set Codec-specific Data
                        setByteBuffer("csd-0", csd0BB)
                        setByteBuffer("csd-1", csd1BB)
                        setByteBuffer("csd-2", csd2BB)
                    }
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(mediaFormat, null, null, 0) // outputFormat = this.outputFormat // option B
                // setCallback(mediaCodecCallback)
                start()
            }
        }.onFailure { LogContext.log.e(TAG, "initAudioDecoder error msg=${it.message}") }
    }

    @Suppress("unused")
    fun decode(audioData: ByteArray) {
        try {
            val decoder: MediaCodec? = audioDecoder
            requireNotNull(decoder) { "Opus decoder must not be null." }

            val bufferInfo = MediaCodec.BufferInfo()

            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = decoder.dequeueInputBuffer(0)
            if (inputIndex > -1) {
                decoder.getInputBuffer(inputIndex)?.run { // Clear exist data.
                    clear() // Put pcm audio data to encoder.
                    put(audioData)
                }
                val pts = computePresentationTimeUs(frameCount.incrementAndGet())
                decoder.queueInputBuffer(inputIndex, 0, audioData.size, pts, 0)
            }

            // Start decoding and get output index
            var outputIndex: Int = decoder.dequeueOutputBuffer(bufferInfo, 0) // Get decoded data in bytes
            while (outputIndex > -1) {
                val chunkPCM = ByteArray(bufferInfo.size)
                decoder.getOutputBuffer(outputIndex)?.run {
                    get(chunkPCM)
                    clear()
                } // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                if (chunkPCM.isNotEmpty()) {
                    ioScope.launch { callback.onDecoded(chunkPCM) }
                }
                decoder.releaseOutputBuffer(outputIndex, false) // Get data again.
                outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            LogContext.log.e(TAG, "You can ignore this message safely. decodeAndPlay error")
        }
    }

    //    private val mediaCodecCallback = object : MediaCodec.Callback() {
    //        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
    //            try {
    //                val inputBuffer = codec.getInputBuffer(inputBufferId)
    //                // fill inputBuffer with valid data
    //                inputBuffer?.clear()
    //                val data = rcvAudioDataQueue.poll()?.also {
    //                    inputBuffer?.put(it)
    //                }
    //                val dataSize = data?.size ?: 0
    //                val pts = computePresentationTimeUs(frameCount.incrementAndGet())
    // //                if (BuildConfig.DEBUG) {
    // //                    LogContext.log.d(TAG, "Data len=$dataSize\t pts=$pts")
    // //                }
    //                codec.queueInputBuffer(inputBufferId, 0, dataSize, pts, 0)
    //            } catch (e: Exception) {
    //                LogContext.log.e(TAG, "Audio Player onInputBufferAvailable error. msg=${e.message}")
    //            }
    //        }
    //
    //        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
    //            try {
    //                val outputBuffer = codec.getOutputBuffer(outputBufferId)
    //                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
    //                // bufferFormat is equivalent to member variable outputFormat
    //                // outputBuffer is ready to be processed or rendered.
    //                outputBuffer?.let {
    //                    val decodedData = ByteArray(info.size)
    //                    it.get(decodedData)
    // //                LogContext.log.w(TAG, "PCM[${decodedData.size}]")
    //                    when (info.flags) {
    //                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
    //                            LogContext.log.w(TAG, "Found CSD0 frame: ${JsonUtil.toJsonString(decodedData)}")
    //                        }
    //                        MediaCodec.BUFFER_FLAG_END_OF_STREAM -> Unit
    //                        MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> Unit
    //                        else -> Unit
    //                    }
    //                    if (decodedData.isNotEmpty()) {
    //                        // Play decoded audio data in PCM
    //                        audioTrack?.write(decodedData, 0, decodedData.size)
    //                    }
    //                }
    //                codec.releaseOutputBuffer(outputBufferId, false)
    //            } catch (e: Exception) {
    //                LogContext.log.e(TAG, "Audio Player onOutputBufferAvailable error. msg=${e.message}")
    //            }
    //        }
    //
    //        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
    //            LogContext.log.w(TAG, "onOutputFormatChanged format=$format")
    //            // Subsequent data will conform to new format.
    //            // Can ignore if using getOutputFormat(outputBufferId)
    //            outputFormat = format // option B
    //        }
    //
    //        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
    //            e.printStackTrace()
    //            LogContext.log.e(TAG, "onError e=${e.message}", e)
    //        }
    //    }

    fun release() {
        runCatching {
            csd0 = null
            csd1 = null
            csd2 = null
            ioScope.cancel()
            audioDecoder?.release()
        }.onFailure { it.printStackTrace() }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / audioDecoderInfo.sampleRate

    interface OpusDecodeCallback {
        fun onDecoded(pcmData: ByteArray)
    }
}
