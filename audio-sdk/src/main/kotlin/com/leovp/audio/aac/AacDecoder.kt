package com.leovp.audio.aac

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.log_sdk.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午5:18
 */
@Suppress("unused")
class AacDecoder(private val audioDecodeInfo: AudioDecoderInfo, private val callback: AacDecodeCallback) {
    companion object {
        private const val TAG = "AacDecoder"
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    //    private var outputFormat: MediaFormat? = null
    private var frameCount = AtomicLong(0)
    private var audioDecoder: MediaCodec? = null

    @Suppress("WeakerAccess")
    var csd0: ByteArray? = null
        private set

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
    @Suppress("unused")
    fun initAudioDecoder(csd0: ByteArray) {
        runCatching {
            this.csd0 = csd0
            LogContext.log.i(TAG, "initAudioDecoder: $audioDecodeInfo")
            val csd0BB = ByteBuffer.wrap(csd0)
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                audioDecodeInfo.sampleRate, audioDecodeInfo.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
                setInteger(MediaFormat.KEY_IS_ADTS, 1)
                // Set ADTS decoder information.
                setByteBuffer("csd-0", csd0BB)
            }
            audioDecoder!!.configure(mediaFormat, null, null, 0)
//            outputFormat = audioDecoder?.outputFormat // option B
//            audioDecoder?.setCallback(mediaCodecCallback)
            audioDecoder?.start()
        }.onFailure { LogContext.log.e(TAG, "initAudioDecoder error msg=${it.message}") }
    }

    /**
     * If I use asynchronous MediaCodec, most of time in my phone(HuaWei Honor V20), it will not play sound due to MediaCodec state error.
     */
    @Suppress("unused")
    fun decode(audioData: ByteArray) {
        try {
            val bufferInfo = MediaCodec.BufferInfo()

            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = audioDecoder?.dequeueInputBuffer(0) ?: -1
            if (inputIndex > -1) {
                audioDecoder?.getInputBuffer(inputIndex)?.run {
                    // Clear exist data.
                    clear()
                    // Put pcm audio data to encoder.
                    put(audioData)
                }
                val pts = computePresentationTimeUs(frameCount.incrementAndGet())
                audioDecoder?.queueInputBuffer(inputIndex, 0, audioData.size, pts, 0)
            }

            // Start decoding and get output index
            var outputIndex: Int = audioDecoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            val chunkPCM = ByteArray(bufferInfo.size)
            // Get decoded data in bytes
            while (outputIndex > -1) {
                audioDecoder?.getOutputBuffer(outputIndex)?.run { get(chunkPCM) }
                // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                if (chunkPCM.isNotEmpty()) {
                    ioScope.launch { callback.onDecoded(chunkPCM) }
                }
                audioDecoder?.releaseOutputBuffer(outputIndex, false)
                // Get data again.
                outputIndex = audioDecoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
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
            ioScope.cancel()
            audioDecoder?.release()
        }.onFailure { it.printStackTrace() }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / audioDecodeInfo.sampleRate

    interface AacDecodeCallback {
        fun onDecoded(pcmData: ByteArray)
    }
}
