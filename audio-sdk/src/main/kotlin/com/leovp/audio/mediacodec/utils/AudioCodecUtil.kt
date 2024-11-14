@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.mediacodec.utils

import com.leovp.audio.mediacodec.bean.OpusCsd
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec
import com.leovp.bytes.readLongLE
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Author: Michael Leo
 * Date: 2023/4/26 15:06
 */
object AudioCodecUtil {
    private const val TAG = "AudioCodecUtil"

    private val emptyCsd = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)

    /**
     * @param data The byte order of [data] must be little endian. If the [data] is obtained from MediaCodec,
     * it's little endian by default.
     */
    fun isOpusConfigFrame(data: ByteArray): Boolean {
        if (data.size < 16) return false
        val aOpusHeaderValue = data.copyOfRange(0, 8).readLongLE()
        return aOpusHeaderValue == IAudioMediaCodec.OPUS_AOPUSHDR
    }

    /**
     * @param byteOrder null means native order.
     */
    fun parseOpusConfigFrame(data: ByteArray, byteOrder: ByteOrder? = null): OpusCsd? {
        if (!isOpusConfigFrame(data)) return null
        return parseOpusConfigFrame(ByteBuffer.wrap(data).also { buf -> byteOrder?.let { order -> buf.order(order) } })
    }

    fun parseOpusConfigFrame(buffer: ByteBuffer): OpusCsd? {
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
            LogContext.log.e(TAG, "Not enough data in OPUS config packet")
            return null
        }
        val id = buffer.long // little endian
        if (id != IAudioMediaCodec.OPUS_AOPUSHDR) {
            LogContext.log.e(TAG, "OPUS header not found")
            return null
        }

        // The length of identification header. In generally, the length is 19 (0x13).
        val idHeaderLength = buffer.long // little endian
        require(idHeaderLength in 0..0x7ffffffe) { "Invalid block size in OPUS header: $idHeaderLength" }
        val idHeaderSize = idHeaderLength.toInt()
        if (buffer.remaining() < idHeaderSize) {
            LogContext.log.e(TAG, "Not enough data in OPUS header (invalid size: $idHeaderSize)")
            return null
        }
        val csd0 = ByteArray(idHeaderSize)
        buffer.get(csd0)

        var csd1: ByteArray? = null
        if (buffer.remaining() > 8) {
            val idDly = buffer.long
            if (idDly == IAudioMediaCodec.OPUS_AOPUSDLY) {
                val idDlyLength = buffer.long
                require(idDlyLength in 0..0x7ffffffe) { "Invalid block size in OPUS DLY: $idDlyLength" }
                val idDlySize = idDlyLength.toInt()
                if (buffer.remaining() < idDlySize) {
                    LogContext.log.e(TAG, "Not enough data in OPUS DLY (invalid size: $idDlySize)")
                    return OpusCsd(csd0, emptyCsd, emptyCsd)
                }
                csd1 = ByteArray(idDlySize)
                buffer.get(csd1)
            }
        }

        var csd2: ByteArray? = null
        if (buffer.remaining() > 8) {
            val idPrl = buffer.long
            if (idPrl == IAudioMediaCodec.OPUS_AOPUSPRL) {
                val idPrlLength = buffer.long
                require(idPrlLength in 0..0x7ffffffe) { "Invalid block size in OPUS PRL: $idPrlLength" }
                val idPrlSize = idPrlLength.toInt()
                if (buffer.remaining() < idPrlSize) {
                    LogContext.log.e(TAG, "Not enough data in OPUS PRL (invalid size: $idPrlSize)")
                    return OpusCsd(csd0, csd1 ?: emptyCsd, emptyCsd)
                }
                csd2 = ByteArray(idPrlSize)
                buffer.get(csd2)
            }
        }

        return OpusCsd(csd0, csd1 ?: emptyCsd, csd2 ?: emptyCsd)
    }
}
