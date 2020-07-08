package com.ho1ho.audiorecord.aac

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.ho1ho.androidbase.utils.LLog
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:05
 */
class AudioEncoder(private val sampleRate: Int, bitrate: Int, private val channelCount: Int) {
    private val bufferInfo: MediaCodec.BufferInfo
    private val presentationTimeUs = AtomicLong(0)
    private var audioEncoder: MediaCodec
    private val outputAacStream: ByteArrayOutputStream = ByteArrayOutputStream()

    @Suppress("WeakerAccess")
    val csd0: ByteArray

    init {
        LLog.w(TAG, "AacEncoder sampleRate=$sampleRate bitrate=$bitrate channelCount=$channelCount")
        csd0 = getAudioEncodingCsd0(
            PROFILE_AAC_LC,
            sampleRate,
            channelCount
        )
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        with(mediaFormat) {
            setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            // setInteger(MediaFormat.KEY_CHANNEL_MASK, DEFAULT_AUDIO_FORMAT)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024)
        }
        audioEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()
        bufferInfo = MediaCodec.BufferInfo()
    }


    /**
     * Encode pcm data to aac.<br></br>
     * <br></br>
     *
     * @param pcmData PCM data.
     */
    fun encodePcmToAac(pcmData: ByteArray): ByteArray {
        // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
        val inputBufferIndex = audioEncoder.dequeueInputBuffer(0)
        if (inputBufferIndex >= 0) {
            val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)
            if (inputBuffer != null) {
                inputBuffer.clear()
                inputBuffer.put(pcmData)
                // inputBuffer.limit(pcmData.length);
            }
            val pts = computePresentationTimeUs(presentationTimeUs.incrementAndGet(), sampleRate)
            audioEncoder.queueInputBuffer(inputBufferIndex, 0, pcmData.size, pts, 0)
        }
        var outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0)
        while (outputBufferIndex >= 0) {
            val outputBuffer = audioEncoder.getOutputBuffer(outputBufferIndex)
            if (outputBuffer != null) {
                val outAacDataSize = bufferInfo.size
                // The length of ADTS header is 7.
                val outAacDataSizeWithAdts = outAacDataSize + 7
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + outAacDataSize)

                // Add ADTS header to pcm data array which length is pcm data length plus 7.
                val outAacDataWithAdts = ByteArray(outAacDataSizeWithAdts)
                addAdtsToDataWithoutCRC(outAacDataWithAdts, outAacDataSizeWithAdts)
                outputBuffer[outAacDataWithAdts, 7, outAacDataSize]
                outputBuffer.position(bufferInfo.offset)
                outputAacStream.write(outAacDataWithAdts)
            }

//            CLog.i(TAG, outAacDataWithAdts.length + " bytes written: " + Arrays.toString(outAacDataWithAdts));
            audioEncoder.releaseOutputBuffer(outputBufferIndex, false)
            outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0)
        }
        outputAacStream.flush()
        val outAacBytes = outputAacStream.toByteArray()
        outputAacStream.reset()
        return outAacBytes
    }

    /**
     * Add 7-bits ADTS header to aac audio data.<br></br>
     * <br></br>
     * https://www.jianshu.com/p/5c770a22e8f8
     * https://blog.csdn.net/tx3344/article/details/7414543
     * https://blog.csdn.net/jay100500/article/details/52955232
     * https://wiki.multimedia.cx/index.php/MPEG-4_Audio#Sampling_Frequencies
     *
     * <br></br>
     * profile
     * 1: Main profile
     * 2: Low Complexity profile(LC)
     * 3: Scalable Sampling Rate profile(SSR)
     * <br></br>
     * sampling_frequency_index
     * 0: 96000 Hz
     * 1: 88200 Hz
     * 2: 64000 Hz
     * 3: 48000 Hz
     * 4: 44100 Hz
     * 5: 32000 Hz
     * 6: 24000 Hz
     * 7: 22050 Hz
     * 8: 16000 Hz
     * 9: 12000 Hz
     * 10: 11025 Hz
     * 11: 8000 Hz
     * 12: 7350 Hz
     * 13: Reserved
     * 14: Reserved
     * 15: frequency is written explicitly
     *
     * <br></br>
     * channel_configuration
     * 0: Defined in AOT Specific Config
     * 1: 1 channel: front-center
     * 2: 2 channels: front-left, front-right
     * 3: 3 channels: front-center, front-left, front-right
     * 4: 4 channels: front-center, front-left, front-right, back-center
     * 5: 5 channels: front-center, front-left, front-right, back-left, back-right
     * 6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
     * 7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
     * 8-15: Reserved
     *
     * @param outAacDataWithAdts    The audio data with ADTS header.
     * @param outAacDataLenWithAdts The length of audio data with ADTS header.
     */
    private fun addAdtsToDataWithoutCRC(outAacDataWithAdts: ByteArray, outAacDataLenWithAdts: Int) {
        LLog.d(TAG, "addAdtsToDataWithoutCRC sampleRate=$sampleRate channelCount=$channelCount")

        // ByteBuffer key
        // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
        // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
        // Convert them to binary value: 0b10, 0b100, 0b1
        // According to AAC required, convert theirs values to binary bits:
        // 00010 0100 0001 000
        // The corresponding hex value：
        // 0001 0010 0000 1000
        // So the csd_0 value is 0x12,0x08

        // https://stackoverflow.com/q/33970002
        // int profile = (csd_data[0] >> 3) & 0x1F;
        // int frequency_idx = ((csd_data[0] & 0x7) << 1) | ((csd_data[1] >> 7) & 0x1);
        // int channels = (csd_data[1] >> 3) & 0xF;
        val profile: Int =
            csd0[0].toInt() shr 3 and 0x1F // AAC LC. If you change this value, DO NOT forget to change KEY_AAC_PROFILE while config MediaCodec
        val freqIdx: Int =
            csd0[0].toInt() and 0x7 shl 1 or (csd0[1].toInt() shr 7 and 0x1) // 4: 44.1KHz 8: 16Khz 11: 8Khz
        val chanCfg: Int = csd0[1].toInt() shr 3 and 0xF // 1: single_channel_element 2: CPE(channel_pair_element)
        LLog.d(TAG, "addAdtsToDataWithoutCRC profile=$profile freqIdx=$freqIdx chanCfg=$chanCfg")

        // https://www.jianshu.com/p/5c770a22e8f8
        outAacDataWithAdts[0] = 0xFF.toByte()
        outAacDataWithAdts[1] = 0xF9.toByte() // No CRC
        outAacDataWithAdts[2] = ((profile - 1 shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        outAacDataWithAdts[3] = ((chanCfg and 3 shl 6) + (outAacDataLenWithAdts shr 11)).toByte()
        outAacDataWithAdts[4] = (outAacDataLenWithAdts and 0x7FF shr 3).toByte()
        outAacDataWithAdts[5] = ((outAacDataLenWithAdts and 7 shl 5) + 0x1F).toByte()
        outAacDataWithAdts[6] = 0xFC.toByte()
    }

    fun stop() {
        try {
            audioEncoder.stop()
            outputAacStream.run {
                flush()
                reset()
            }
        } catch (e: Exception) {
            LLog.e(TAG, "stop error.", e)
        }
    }

    /**
     * Release sources.
     */
    fun release() {
        stop()
        try {
            audioEncoder.release()
            outputAacStream.close()
        } catch (e: Exception) {
            LLog.e(TAG, "release error.", e)
        }
    }

    // https://cloud.tencent.com/developer/ask/61404
    @Suppress("SameParameterValue")
    private fun getAudioEncodingCsd0(aacProfile: Int, sampleRate: Int, channelCount: Int): ByteArray {
        val freqIdx = getSampleFrequencyIndex(sampleRate)
        val csd = ByteBuffer.allocate(2)
        csd.put(0, (aacProfile shl 3 or freqIdx shr 1).toByte())
        csd.put(1, (freqIdx and 0x01 shl 7 or channelCount shl 3).toByte())
        val csd0 = ByteArray(2)
        csd.get(csd0)
        csd.clear()
        return csd0
    }

    companion object {
        private const val TAG = "AuEN"
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
        fun getSampleFrequencyIndex(sampleRate: Int): Int {
            return when (sampleRate) {
                7350 -> 12
                8000 -> 11
                11025 -> 10
                12000 -> 9
                16000 -> 8
                22050 -> 7
                24000 -> 6
                32000 -> 5
                44100 -> 4
                48000 -> 3
                64000 -> 2
                88200 -> 1
                96000 -> 0
                else -> -1
            }
        }

        /**
         * Calculate PTS.
         * Actually, it doesn't make any error if you return 0 directly.
         *
         * @return The calculated presentation time in microseconds.
         */
        @kotlin.jvm.JvmStatic
        fun computePresentationTimeUs(frameIndex: Long, sampleRate: Int): Long {
            val result = frameIndex * 1000000L / sampleRate
            LLog.d(TAG, "computePresentationTimeUs=$result")
            return result
        }
    }
}