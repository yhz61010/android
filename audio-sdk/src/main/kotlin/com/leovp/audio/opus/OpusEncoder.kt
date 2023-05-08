@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.opus

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodecAsynchronous
import com.leovp.audio.mediacodec.utils.AudioCodecUtil
import com.leovp.bytes.readLongLE
import com.leovp.bytes.toByteArray
import com.leovp.bytes.toHexString
import com.leovp.kotlin.exts.formatDecimalSeparator
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * https://github.com/inodevip/OpusLibAndroidDemo
 * https://github.com/apon/opus-android
 * https://datatracker.ietf.org/doc/html/rfc6716
 *
 * - Opus supports bitrates: 6kbit/s ~ 510 kbit/s
 * - Opus supports frame size in milliseconds: 2.5, 5, 10, 20, 40, 60 (20 ms frames are a good choice for most applications)
 * - Opus supports sample rate: 8kHz ~ 48kHz. 8kHz, 12kHz, 16kHz, 24kHz, 48Khz
 *
 * We use 8kHz as example, the opus supports the following frame size:
 * 8000 / 1000 = 8
 *
 * 8 * 2.5 =  20
 * 8 * 5   =  40
 * 8 * 10  =  80
 * 8 * 20  = 160
 * 8 * 40  = 320
 * 8 * 60  = 480
 *
 * Encode:
 * ```pseudocode
 * val opusBytes = ByteArray(1000)
 * encoder.encode(pcmBytes, opusBytes)
 * ```
 *
 * Decode:
 * ```pseudocode
 * // 16 bit per sample (bit depth), so we use short type(2 bytes) to store per sample.
 * val pcmBytes = ShortArray(FRAME_SIZE * NUM_CHANNELS)
 * decoder.decode(opusBytes, pcmBytes)
 * ```
 *
 * https://datatracker.ietf.org/doc/html/rfc6716
 * https://www.rfc-editor.org/rfc/rfc7845#section-5.1
 * https://developer.android.com/reference/android/media/MediaCodec#CSD
 *
 * Identification Header
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      'O'      |      'p'      |      'u'      |      's'      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      'H'      |      'e'      |      'a'      |      'd'      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Version = 1  | Channel Count |           Pre-skip            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     Input Sample Rate (Hz)                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Output Gain (Q7.8 in dB)    | Mapping Family|               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+               :
 * |                                                               |
 * :               Optional Channel Mapping Table...               :
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The fields in the identification (ID) header have the following meaning:
 *
 * 1. Magic Signature:
 *      The magic numbers: "OpusHead"，8-octet (64-bits). 0x4F 'O' | 0x70 'p' | 0x75 'u' | 0x73 's' | 0x48 'H' | 0x65 'e' | 0x61 'a' | 0x64 'd'
 * 2. Version (8 bits, unsigned):
 *      Must always be `1`.
 * 3. Output Channel Count (8 bits, unsigned):
 *      This is the number of output channels. This value MUST NOT be zero.
 * 4. Pre-skip (16 bits, unsigned, little endian):
 *      This is the number of samples (at 48 kHz) to discard from the decoder output when starting playback, and also the number to
 *      subtract from a page's granule position to calculate its PCM sample position.
 * 5. Input Sample Rate (32 bits, unsigned, little endian):
 *      This is the sample rate of the original input (before encoding), in Hz. This field is _not_ the sample rate to use for playback of the encoded data.
 * 6. Output Gain (16 bits, signed, little endian):
 *      This is a gain to be applied when decoding.
 * 7. Channel Mapping Family (8 bits, unsigned):
 *      This octet indicates the order and semantic meaning of the output channels.
 * 8. Channel Mapping Table (Optional):
 *      This table defines the mapping from encoded streams to output channels.
 *      This field MUST be omitted when the channel mapping family is 0, but is REQUIRED otherwise.
 *
 * For Android MediaCodec csd:
 * CSD buffer #0:
 *      Identification header
 * CSD buffer #1:
 *      Pre-skip in nanosecs (unsigned 64-bit native-order integer.) This overrides the pre-skip value in the identification header.
 * CSD buffer #2:
 *      Seek Pre-roll in nanosecs (unsigned 64-bit native-order integer.)
 *
 * Here is an example of the config packet received for an OPUS stream (From MediaCodec):
 * 00000000  41 4f 50 55 53 48 44 52  13 00 00 00 00 00 00 00  |AOPUSHDR........|
 * -------------- BELOW IS THE PART WE MUST PUT AS EXTRADATA  -------------------
 * 00000010  4f 70 75 73 48 65 61 64  01 02 38 01 80 bb 00 00  |OpusHead..8.....|  <- Identification header
 * 00000020  00 00 00                                          |...             |  <- Identification header
 * ------------------------------------------------------------------------------
 * 00000020           41 4f 50 55 53  44 4c 59 08 00 00 00 00  |   AOPUSDLY.....|
 * 00000030  00 00 00 a0 2e 63 00 00  00 00 00                 |.....c.....     |
 * 00000030                                    41 4f 50 55 53  |           AOPUS|
 * 00000040  50 52 4c 08 00 00 00 00  00 00 00 00 b4 c4 04 00  |PRL.............|
 * 00000050  00 00 00                                          |...|
 *
 * The meaning of each part:
 * 41 4f 50 55 53 48 44 52 -> AOPUSHDR
 * 13 00 00 00 00 00 00 00 -> The length of identification header. Decimal value: 19
 *
 * The meaning of identification header (CSD-0):
 * 4f 70 75 73 48 65 61 64  -> OpusHead
 * 01                       -> Version
 * 02                       -> Output Channel Count
 * 38 01                    -> Pre-skip. Decimal value: 312
 * 80 bb 00 00              -> Input Sample Rate (Hz). Decimal value: 48000
 * 00 00                    -> Output Gain
 * 00                       -> Channel Mapping Family
 *
 * 41 4f 50 55 53 44 4c 59 -> AOPUSDLY (Get the definition from chatGPT)
 * 08 00 00 00 00 00 00 00 -> The length of "Pre-skip"
 * a0 2e 63 00 00 00 00 00 -> Decimal value: 6,500,000 (CSD-1)
 *
 * 41 4f 50 55 53 50 52 4c -> AOPUSPRL (Get the definition from chatGPT)
 * 08 00 00 00 00 00 00 00 -> The length of "Seek Pre-roll"
 * 00 b4 c4 04 00 00 00 00 -> Decimal value: 80,000,000 (CSD-2)
 *
 * Each "section" is prefixed by a 64-bit ID and a 64-bit length.
 * <https://developer.android.com/reference/android/media/MediaCodec#CSD>
 *
 * Author: Michael Leo
 * Date: 2023/4/14 15:17
 */
class OpusEncoder(
    sampleRate: Int,
    channelCount: Int,
    private val bitrate: Int,
    private val callback: IEncodeCallback) : BaseMediaCodecAsynchronous(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount, true) {
    companion object {
        private const val TAG = "OpusEn"
    }

    val queue = ArrayBlockingQueue<ByteArray>(64)

    var csd0: ByteArray? = null
        private set
    var csd1: ByteArray? = null
        private set
    var csd2: ByteArray? = null
        private set

    override fun setFormatOptions(format: MediaFormat) {
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        // format.setInteger(MediaFormat.KEY_COMPLEXITY, 3)
    }

    override fun onInputData(inBuf: ByteBuffer): Int {
        return queue.poll()?.let {
            inBuf.put(it)
            it.size
        } ?: 0
    }

    override fun onOutputData(outBuf: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean) {
        if (isConfig) {
            LogContext.log.w(TAG, "Found config frame.")
            val opusCsd = AudioCodecUtil.parseOpusConfigFrame(outBuf) // little endian
            csd0 = opusCsd?.csd0
            csd1 = opusCsd?.csd1
            csd2 = opusCsd?.csd2
            LogContext.log.w(TAG, "csd0[${csd0?.size}] HEX[${csd0?.toHexString()}]")
            LogContext.log.w(TAG, "csd1[${csd1?.size}]=${csd1?.readLongLE()?.formatDecimalSeparator()} HEX[${csd1?.toHexString()}]")
            LogContext.log.w(TAG, "csd2[${csd2?.size}]=${csd2?.readLongLE()?.formatDecimalSeparator()} HEX[${csd2?.toHexString()}]")
            outBuf.flip()
        }
        callback.onEncoded(outBuf.toByteArray(), isConfig, isKeyFrame)
    }

    override fun stop() {
        queue.clear()
        super.stop()
    }

    override fun release() {
        csd0 = null
        csd1 = null
        csd2 = null
        super.release()
    }
}
