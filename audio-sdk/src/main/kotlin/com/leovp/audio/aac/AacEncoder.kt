package com.leovp.audio.aac

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodec
import com.leovp.bytes.toHexStringLE
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:05
 */
class AacEncoder(
    sampleRate: Int,
    channelCount: Int,
    private val bitrate: Int,
    private val callback: IEncodeCallback) : BaseMediaCodec(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount, true) {
    companion object {
        private const val TAG = "AacEn"
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    val queue = ArrayBlockingQueue<ByteArray>(10)

    var csd0: ByteArray? = null
        private set

    override fun setFormatOptions(format: MediaFormat) {
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        // setInteger(MediaFormat.KEY_CHANNEL_MASK, DEFAULT_AUDIO_FORMAT)
    }

    override fun onInputData(): ByteArray? {
        return queue.poll()
    }

    override fun onOutputData(outData: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean) {
        if (isConfig) {
            LogContext.log.w(TAG, "Found config frame.")
            val outBytes = ByteArray(outData.remaining())
            outData.get(outBytes)
            csd0 = outBytes
            LogContext.log.w(TAG, "csd0[${csd0?.size}]=HEX[${csd0?.toHexStringLE()}]")
        }
        val aacDataLength = info.size
        // The length of ADTS header is 7.
        val aacDataSizeWithAdtsLength = aacDataLength + 7
        outData.position(info.offset)
        outData.limit(info.offset + aacDataLength)

        // Add ADTS header to pcm data array which length is pcm data length plus 7.
        val aacDataWithAdts = ByteArray(aacDataSizeWithAdtsLength)
        addAdtsToDataWithoutCRC(aacDataWithAdts, aacDataSizeWithAdtsLength)
        outData.get(aacDataWithAdts, 7, aacDataLength)
        outData.position(info.offset)
        callback.onEncoded(aacDataWithAdts, isConfig, isKeyFrame)
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
        // if (BuildConfig.DEBUG) {
        //     LogContext.log.d(TAG, "addAdtsToDataWithoutCRC sampleRate=$sampleRate channelCount=$channelCount")
        // }

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

        if (csd0 == null) {
            LogContext.log.e(TAG, "csd0 can not be null")
        }
        csd0?.let {
            // AAC LC. If you change this value, DO NOT forget to change KEY_AAC_PROFILE while config MediaCodec
            val profile: Int = (it[0].toInt() shr 3) and 0x1F
            // 4: 44.1KHz 8: 16Khz 11: 8Khz
            val freqIdx: Int = ((it[0].toInt() and 0x7) shl 1) or ((it[1].toInt() shr 7) and 0x1)
            // 1: single_channel_element 2: CPE(channel_pair_element)
            val channelCfg: Int = (it[1].toInt() shr 3) and 0xF
            // if (BuildConfig.DEBUG) {
            //     LogContext.log.d(TAG, "addAdtsToDataWithoutCRC profile=$profile freqIdx=$freqIdx channelCfg=$channelCfg")
            // }

            // https://www.jianshu.com/p/5c770a22e8f8
            outAacDataWithAdts[0] = 0xFF.toByte()
            outAacDataWithAdts[1] = 0xF9.toByte() // No CRC  // With CRC 0xF1
            outAacDataWithAdts[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (channelCfg shr 2)).toByte()
            outAacDataWithAdts[3] = (((channelCfg and 3) shl 6) + (outAacDataLenWithAdts shr 11)).toByte()
            outAacDataWithAdts[4] = ((outAacDataLenWithAdts and 0x7FF) shr 3).toByte()
            outAacDataWithAdts[5] = (((outAacDataLenWithAdts and 7) shl 5) + 0x1F).toByte()
            outAacDataWithAdts[6] = 0xFC.toByte()
        }
    }

    // https://cloud.tencent.com/developer/ask/61404
    // FIXME
    // Has bugs!!! when parameter are 2(AAC LC), 8(16Khz), 1(mono)
    @Suppress("SameParameterValue", "unused")
    private fun getAudioEncodingCsd0(aacProfile: Int, sampleRate: Int, channelCount: Int): ByteArray? {
        val freqIdx = getSampleFrequencyIndex(sampleRate)
        if (freqIdx == -1) return null
        val csd = ByteBuffer.allocate(2)
        csd.put(0, (aacProfile shl 3 or freqIdx shr 1).toByte())
        csd.put(1, ((freqIdx and 0x01) shl 7 or channelCount shl 3).toByte())
        val csd0 = ByteArray(2)
        csd.get(csd0)
        csd.clear()
        return csd0
    }

    /**
     * Calculate PTS.
     * Actually, it doesn't make any error if you return 0 directly.
     *
     * @return The calculated presentation time in microseconds.
     */
    private fun computePresentationTimeUs(frameIndex: Long, sampleRate: Int): Long {
        //        LogContext.log.d(TAG, "computePresentationTimeUs=$result")
        return frameIndex * 1000000L / sampleRate
    }

    private fun getSampleFrequencyIndex(sampleRate: Int): Int {
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
}
