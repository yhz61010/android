@file:Suppress("MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.media

import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext

/**
 * NALU类型，将其转为二进制数据后，解读顺序为从左往右，如下:
 *
 * +---------------+
 * |0|1|2|3|4|5|6|7|
 * +-+-+-+-+-+-+-+-+
 * |F|NRI| Type    |
 * +---------------+
 *
 *（1）第1位禁止位，值为1表示语法出错
 *（2）第2~3位为参考级别。重要级别，0b11(3)表示非常重要。
 *（3）第4~8为是nal单元类型
 * 示例1： 0x67(0110 0111)(103) or 0x27(0010 0111)(39)
 * 从左往右4-8位为0 0111，转为十进制7，7对应序列参数集 NALU_TYPE_SPS(序列参数集)(Sequence parameter set)
 *
 * 示例2： 0x68(0110 1000)(104) or 0x28(0010 1000)(40)
 * 从左往右4-8位为0 1000，转为十进制8，8对应序列参数集 NALU_TYPE_PPS(图像参数集)(Picture parameter set)
 *
 * 示例3： 0x65(0110 0101)(101) or 0x25(0010 0101)(37)
 * 从左往右4-8位为0 0101，转为十进制5，5对应 NALU_TYPE_IDR 图像中的片(I帧)
 *
 * 示例4： 0x41(0100 0001)(65) or 0x21(0010 0001)(33)
 * 从左往右4-8位为0 0001，转为十进制1，1对应非 NALU_TYPE_IDR 图像中的片(P帧 or B帧)
 *
 * NALU类型 & 0001 1111(0x1F)(31) = 5 即 NALU类型 & 31(十进制) = 5
 *
 * https://blog.csdn.net/tantion/article/details/82703228
 *
 * Author: Michael Leo
 * Date: 19-10-29 下午2:54
 */
@Suppress("unused")
object H264Util {
    private const val TAG = "H264Util"
    private const val DEBUG = false
    private const val NALU_TYPE_SPS = 7
    private const val NALU_TYPE_PPS = 8
    private const val NALU_TYPE_IDR = 5
    private const val NALU_TYPE_NONE_IDR = 1

    // NALU_TYPE_IDR and NALU_TYPE_SPS frame are considered as Key frame
    fun isKeyFrame(data: ByteArray): Boolean {
        return isIdrFrame(data) || isSps(
            data
        )
    }

    /**
     * Check whether current frame is key frame.
     *
     * @param data The video data.
     * @return Whether this frame is key frame.
     */
    fun isIdrFrame(data: ByteArray): Boolean {
        return getNaluType(data) == NALU_TYPE_IDR // 5 0x65(101) or 0x25(37)
    }

    fun isNoneIdrFrame(data: ByteArray): Boolean {
        return getNaluType(data) == NALU_TYPE_NONE_IDR // 1 0x41(65) or 0x21(33)
    }

    fun isSps(data: ByteArray): Boolean { // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // 7: NALU_TYPE_SPS, 8: NALU_TYPE_PPS, 5: I Frame, 1: P Frame
        return getNaluType(data) == NALU_TYPE_SPS // 7 0x67(103) or 0x27(39)
    }

    fun isPps(data: ByteArray): Boolean { // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // 7: NALU_TYPE_SPS, 8: NALU_TYPE_PPS, 5: I Frame, 1: P Frame
        return getNaluType(data) == NALU_TYPE_PPS // 8 0x68(104) or 0x28(40)
    }

    /**
     * @param data The following example contains both NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,67,42,80,28,DA,1,10,F,1E,5E,6A,A,C,A,D,A1,42,6A,0,0,0,1,68,CE,6,E2
     *
     * @return The returned sps data contains the delimiter prefix 0,0,0,1
     */
    fun getSps(data: ByteArray): ByteArray? {
        val isSps = isSps(data)
        return if (!isSps) {
            null
        } else try {
            // The following example contains both NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,67,42,80,28,DA,1,10,F,1E,5E,6A,A,C,A,D,A1,42,6A,0,0,0,1,68,CE,6,E2
            for (i in 5 until data.size) {
                if (CodecUtil.findStartCode(data, i)) {
                    val sps = ByteArray(i)
                    System.arraycopy(data, 0, sps, 0, i)
                    return sps
                }
            }
            data
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getSps error msg=${e.message}")
            null
        }
    }

    /**
     * @param data The following example contains both NALU_TYPE_SPS, NALU_TYPE_PPS and first video data(All data are in hexadecimal)
     * Example: 0,0,0,1,67,42,80,28,DA,1,10,F,1E,5E,6A,A,C,A,D,A1,42,6A,0,0,0,1,68,CE,6,E2,0,0,0,1,65,8B,4,B0,7C,F1
     *
     * @return The returned pps data contains the delimiter prefix 0,0,0,1
     */
    fun getPps(data: ByteArray): ByteArray? {
        if (!CodecUtil.findStartCode(data)) return null
        return try {
            // The following example contains both NALU_TYPE_SPS, NALU_TYPE_PPS and first video data(All data are in hexadecimal)
            // Example1: 0,0,0,1,67,42,80,28,DA,1,10,F,1E,5E,6A,A,C,A,D,A1,42,6A,0,0,0,1,68,CE,6,E2,0,0,0,1,65,8B,4,B0,7C,F1
            // Example2: 0,0,0,1,67,42,80,28,DA,1,10,F,1E,5E,6A,A,C,A,D,A1,42,6A,0,0,0,1,68,CE,6,E2
            // Example3: 0,0,0,1,68,CE,6,E2,0,0,0,1,65,8B,4,B0,7C,F1
            // Example4: 0,0,0,1,68,CE,6,E2
            var startIndex = 0
            for (i in 3 until data.size) {
                if (CodecUtil.findStartCode(data, i)) {
                    val ppsLength = i - startIndex
                    val pps = ByteArray(ppsLength)
                    System.arraycopy(data, startIndex, pps, 0, ppsLength)
                    if (isPps(pps)) return pps else startIndex = i
                }
            }
            if (startIndex > 0) {
                val ppsLength = data.size - startIndex
                val pps = ByteArray(ppsLength)
                System.arraycopy(data, startIndex, pps, 0, ppsLength)
                if (isPps(pps)) return pps
            }
            if (isPps(data)) data else null
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getPps error msg=${e.message}")
            null
        }
    }

    fun getNaluType(data: ByteArray): Int {
        if (data.size < 5) {
            if (DEBUG) LogContext.log.d(TAG, "Invalid H264 data length. Length: ${data.size}")
            return -1
        }

        if (DEBUG) {
            LogContext.log.d(
                TAG,
                "Frame HEX data[0~4]=${data[0].toHexString()},${data[1].toHexString()}," +
                    "${data[2].toHexString()},${data[3].toHexString()},${data[4].toHexString()}"
            )
        }
        return if (data[0].toInt() != 0x0 || data[1].toInt() != 0x0 && data[2].toInt() != 0x0 || data[3].toInt() != 0x1) {
            LogContext.log.d(TAG, "Not valid H264 data.")
            -1
        } else {
            val nalu = data[4]
            nalu.toInt() and 0x1F
        }
    }

    fun getNaluType(naluByte: Byte): Int = naluByte.toInt() and 0x1F

    @Suppress("unused")
    fun getNaluTypeName(naluType: Int): String {
        return when (naluType) {
            NALU_TYPE_SPS -> "SPS"
            NALU_TYPE_PPS -> "PPS"
            NALU_TYPE_IDR -> "I"
            NALU_TYPE_NONE_IDR -> "B/P"
            else -> "Unknown"
        }
    }

    fun getNaluTypeName(data: ByteArray): String = getNaluTypeName(getNaluType(data))

    fun getAvcCodec(encoder: Boolean = true): List<MediaCodecInfo> = CodecUtil.getCodecListByMimeType(MediaFormat.MIMETYPE_VIDEO_AVC, encoder)
}
