package com.leovp.audio.opus

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodec
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec.Companion.OPUS_AOPUSDLY
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec.Companion.OPUS_AOPUSHDR
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec.Companion.OPUS_AOPUSPRL
import com.leovp.bytes.readLongLE
import com.leovp.bytes.toByteArray
import com.leovp.bytes.toHexStringLE
import com.leovp.kotlin.exts.formatDecimalSeparator
import com.leovp.log.LogContext
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/*
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
    private val callback: IEncodeCallback) : BaseMediaCodec(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount) {
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
    }

    override fun onInputData(): ByteArray? {
        return queue.poll()
    }

    override fun onOutputData(outData: ByteBuffer, isConfig: Boolean, isKeyFrame: Boolean) {
        if (isConfig) {
            val opusCsd = getOpusConfigPacket(outData) // little endian
            csd0 = opusCsd.csd0
            csd1 = opusCsd.csd1
            csd2 = opusCsd.csd2
            LogContext.log.i(TAG, "csd0[${csd0?.size}]=HEX[${csd0?.toHexStringLE()}]")
            LogContext.log.i(TAG,
                "csd1[${csd1?.size}]=${csd1?.readLongLE()?.formatDecimalSeparator()} HEX[${csd1?.toHexStringLE()}]")
            LogContext.log.i(TAG,
                "csd2[${csd2?.size}]=${csd2?.readLongLE()?.formatDecimalSeparator()} HEX[${csd2?.toHexStringLE()}]")
            outData.flip()
        }
        callback.onEncoded(outData.toByteArray(), isConfig, isKeyFrame)
    }

    private fun getOpusConfigPacket(buffer: ByteBuffer): OpusCsd {
        /*
        Here is an example of the config packet received for an OPUS stream (From MediaCodec):

        00000000  41 4f 50 55 53 48 44 52  13 00 00 00 00 00 00 00  |AOPUSHDR........|
        -------------- BELOW IS THE PART WE MUST PUT AS EXTRADATA  -------------------
        00000010  4f 70 75 73 48 65 61 64  01 02 38 01 80 bb 00 00  |OpusHead..8.....|  <- Identification header
        00000020  00 00 00                                          |...             |  <- Identification header
        ------------------------------------------------------------------------------
        00000020           41 4f 50 55 53  44 4c 59 08 00 00 00 00  |   AOPUSDLY.....|
        00000030  00 00 00 a0 2e 63 00 00  00 00 00 41 4f 50 55 53  |.....c.....AOPUS|
        00000040  50 52 4c 08 00 00 00 00  00 00 00 00 b4 c4 04 00  |PRL.............|
        00000050  00 00 00                                          |...|

        The meaning of each part:
        41 4f 50 55 53 48 44 52 -> AOPUSHDR
        13 00 00 00 00 00 00 00 -> The length of identification header. Decimal value: 19

        The meaning of identification header (CSD-0):
        4f 70 75 73 48 65 61 64  -> OpusHead
        01                       -> Version
        02                       -> Output Channel Count
        38 01                    -> Pre-skip. Decimal value: 312
        80 bb 00 00              -> Input Sample Rate (Hz). Decimal value: 48000
        00 00                    -> Output Gain
        00                       -> Channel Mapping Family

        41 4f 50 55 53 44 4c 59 -> AOPUSDLY (Get the definition from chatGPT)
        08 00 00 00 00 00 00 00 -> The length of "Pre-skip"
        a0 2e 63 00 00 00 00 00 -> Decimal value: 6,500,000 (CSD-1)

        41 4f 50 55 53 50 52 4c -> AOPUSPRL (Get the definition from chatGPT)
        08 00 00 00 00 00 00 00 -> The length of "Seek Pre-roll"
        00 b4 c4 04 00 00 00 00 -> Decimal value: 80,000,000 (CSD-2)

        Each "section" is prefixed by a 64-bit ID and a 64-bit length.

        <https://developer.android.com/reference/android/media/MediaCodec#CSD>
        */
        if (buffer.remaining() < 16) {
            throw IOException("Not enough data in OPUS config packet")
        }
        val id = buffer.long // little endian
        if (id != OPUS_AOPUSHDR) {
            throw IOException("OPUS header not found")
        }

        // The length of identification header. In generally, the length is 19 (0x13).
        val idHeaderLength = buffer.long // little endian
        if (idHeaderLength < 0 || idHeaderLength >= 0x7FFFFFFF) {
            throw IOException("Invalid block size in OPUS header: $idHeaderLength")
        }
        val idHeaderSize = idHeaderLength.toInt()
        if (buffer.remaining() < idHeaderSize) {
            throw IOException("Not enough data in OPUS header (invalid size: $idHeaderSize)")
        }
        val csd0 = ByteArray(idHeaderSize)
        buffer.get(csd0)

        var csd1: ByteArray? = null
        if (buffer.remaining() > 8) {
            val idDly = buffer.long
            if (idDly == OPUS_AOPUSDLY) {
                val idDlyLength = buffer.long
                if (idDlyLength < 0 || idDlyLength >= 0x7FFFFFFF) {
                    throw IOException("Invalid block size in OPUS DLY: $idDlyLength")
                }
                val idDlySize = idDlyLength.toInt()
                if (buffer.remaining() < idDlySize) {
                    throw IOException("Not enough data in OPUS DLY (invalid size: $idDlySize)")
                }
                csd1 = ByteArray(idDlySize)
                buffer.get(csd1)
            }
        }

        var csd2: ByteArray? = null
        if (buffer.remaining() > 8) {
            val idPrl = buffer.long
            if (idPrl == OPUS_AOPUSPRL) {
                val idPrlLength = buffer.long
                if (idPrlLength < 0 || idPrlLength >= 0x7FFFFFFF) {
                    throw IOException("Invalid block size in OPUS PRL: $idPrlLength")
                }
                val idPrlSize = idPrlLength.toInt()
                if (buffer.remaining() < idPrlSize) {
                    throw IOException("Not enough data in OPUS PRL (invalid size: $idPrlSize)")
                }
                csd2 = ByteArray(idPrlSize)
                buffer.get(csd2)
            }
        }

        return OpusCsd(csd0, csd1 ?: byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0), csd2 ?: byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        LogContext.log.i(TAG, "onOutputFormatChanged format=$format")
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        LogContext.log.e(TAG, "onError e=${e.message}")
    }

    private data class OpusCsd(val csd0: ByteArray, val csd1: ByteArray, val csd2: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as OpusCsd

            if (!csd0.contentEquals(other.csd0)) return false
            if (!csd1.contentEquals(other.csd1)) return false
            if (!csd2.contentEquals(other.csd2)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = csd0.contentHashCode()
            result = 31 * result + csd1.contentHashCode()
            result = 31 * result + csd2.contentHashCode()
            return result
        }
    }
}
