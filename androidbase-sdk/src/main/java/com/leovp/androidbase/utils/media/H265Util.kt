package com.leovp.androidbase.utils.media

import com.leovp.androidbase.exts.kotlin.toHexString
import com.leovp.androidbase.utils.log.LogContext

/**
 * [H265 NAL Unit Header](https://tools.ietf.org/html/rfc7798#page-13)
 *
 * ```plain
 * +---------------+---------------+
 * |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |F|   Type    |  LayerId  | TID |
 * +-------------+-----------------+
 * ```
 *
 * - F: 1 bit
 *
 * forbidden_zero_bit.  Required to be zero in HEVC.  Note that the
 * inclusion of this bit in the NAL unit header was to enable
 * transport of HEVC video over MPEG-2 transport systems (avoidance
 * of start code emulations) MPEG2S.  In the context of this memo,
 * the value 1 may be used to indicate a syntax violation, e.g., for
 * a NAL unit resulted from aggregating a number of fragmented units
 * of a NAL unit but missing the last fragment, as described in Section 4.4.3.
 *
 * - Type: 6 bits
 *
 * nal_unit_type.  This field specifies the NAL unit type as defined
 * in Table 7-1 of HEVC.  If the most significant bit of this field
 * of a NAL unit is equal to 0 (i.e., the value of this field is less
 * than 32), the NAL unit is a VCL NAL unit.  Otherwise, the NAL unit
 * is a non-VCL NAL unit.  For a reference of all currently defined
 * NAL unit types and their semantics, please refer to Section 7.4.2
 * in HEVC.
 *
 * - LayerId: 6 bits
 *
 * nuh_layer_id.  Required to be equal to zero in HEVC.  It is
 * anticipated that in future scalable or 3D video coding extensions
 * of this specification, this syntax element will be used to
 * identify additional layers that may be present in the CVS, wherein
 * a layer may be, e.g., a spatial scalable layer, a quality scalable
 * layer, a texture view, or a depth view.
 *
 * - TID: 3 bits
 *
 * nuh_temporal_id_plus1.  This field specifies the temporal
 * identifier of the NAL unit plus 1.  The value of TemporalId is
 * equal to TID minus 1.  A TID value of 0 is illegal to ensure that
 * there is at least one bit in the NAL unit header equal to 1, so to
 * enable independent considerations of start code emulations in the
 * NAL unit header and in the NAL unit payload data.
 *
 * ```hexadecimal
 * 00 00 00 01 40 01  nal_unit_type 值为 32， VPS 语义为视频参数集
 * 00 00 00 01 42 01  nal_unit_type 值为 33， SPS 语义为序列参数集
 * 00 00 00 01 44 01  nal_unit_type 值为 34， PPS 语义为图像参数集
 * 00 00 00 01 4E 01  nal_unit_type 值为 39， SEI 语义为补充增强信息
 * 00 00 00 01 26 01  nal_unit_type 值为 19， IDR 语义为可能有 RADL 图像的 IDR 图像的 SS(Slice Segment) 编码数据
 * 00 00 00 01 02 01  nal_unit_type 值为  1， P   语义为被参考的后置图像，且非 TSA、非 STSA 的 SS(Slice Segment) 编码数据
 * ```
 *
 * Example:
 * 0x40 0x01 = 0b 0100 0000 0000 0001
 * ```plain
 * +---------------+---------------+
 * |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |F|   Type    |  LayerId  | TID |
 * |0|1 0 0 0 0 0|0 0 0 0 0 0|0 0 1|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 * The NALU type is 0b0010_0000 = 0x20 = 32
 * or you can calculate it like this:
 * Example:
 * (0x40 & 0x7E) >> 1
 *   0b0100_0000
 * & 0b0111_1110
 * --------------
 *   0b0100_0000 >> 1 = 0b0010_0000 = 0x20 = 32
 *
 * or you can also calculate it like this:
 * Example:
 * (0x40 >> 1) & 0x3F
 *   0b0010_0000
 * & 0b0011_1111
 * --------------
 *   0b0010_0000 = 0x20 = 32
 *
 * So the full NALU header value are:
 * F=0
 * Type=32
 * LayerId=0
 * TID=1
 *
 * According to some articles(Not verified), NALU type from 0~9 indicates P frame, 16~21 indicates I frame.
 *
 * Author: Michael Leo
 * Date: 2021/5/13 3:21 PM
 */
object H265Util {
    private const val TAG = "H265Util"
    private const val DEBUG = false

    const val NALU_TYPE_VPS = 32 // 0x20
    const val NALU_TYPE_SPS = 33 // 0x21
    const val NALU_TYPE_PPS = 34 // 0x22
    const val NALU_TYPE_SEI = 39 // 0x27
    const val NALU_TYPE_IDR = 19 // 0x13

    fun isIdrFrame(data: ByteArray): Boolean {
        return getNaluType(data) == NALU_TYPE_IDR // 19
    }

    fun isVps(data: ByteArray): Boolean {
        return getNaluType(data) == NALU_TYPE_VPS // 32
    }

    fun isSps(data: ByteArray): Boolean {
        return getNaluType(data) == NALU_TYPE_SPS // 33
    }

    fun isPps(data: ByteArray): Boolean {
        return getNaluType(data) == NALU_TYPE_PPS // 34
    }

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     *
     * @return The returned sps data contains the delimiter prefix 0,0,0,1
     */
    fun getVps(data: ByteArray): ByteArray? {
        val isVps = isVps(data)
        return if (!isVps) {
            null
        } else try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            for (i in 5 until data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    val vps = ByteArray(i)
                    System.arraycopy(data, 0, vps, 0, i)
                    return vps
                }
            }
            null
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getVps error msg=${e.message}")
            null
        }
    }

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     *
     * @return The returned sps data contains the delimiter prefix 0,0,0,1
     */
    fun getSps(data: ByteArray): ByteArray? {
        val isSps = isSps(data)
        if (isSps) {
            return data
        }
        return if (!isVps(data)) {
            null
        } else try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            var startIndex = -1
            for (i in 5 until data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    if (startIndex < 0) {
                        startIndex = i
                    } else {
                        val spsLength = i - startIndex
                        val sps = ByteArray(spsLength)
                        System.arraycopy(data, startIndex, sps, 0, spsLength)
                        return sps
                    }
                }
            }
            if (startIndex > -1) data.copyOfRange(startIndex, data.size) else null
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getSps error msg=${e.message}")
            null
        }
    }

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     *
     * @return The returned sps data contains the delimiter prefix 0,0,0,1
     */
    fun getPps(data: ByteArray): ByteArray? {
        val isPps = isPps(data)
        if (isPps) {
            return data
        }
        return if (!isVps(data)) {
            null
        } else try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            var startIndex = -1
            var prefixOccurTimes = 1
            for (i in 5 until data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    if (++prefixOccurTimes < 3) continue
                    if (startIndex < 0) {
                        startIndex = i
                    } else {
                        val ppsLength = i - startIndex
                        val pps = ByteArray(ppsLength)
                        System.arraycopy(data, startIndex, pps, 0, ppsLength)
                        return pps
                    }
                }
            }
            if (startIndex > -1) data.copyOfRange(startIndex, data.size) else null
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getPps error msg=${e.message}")
            null
        }
    }

    fun getNaluType(data: ByteArray): Int {
        if (data.size < 5) {
            if (DEBUG) LogContext.log.d(TAG, "Invalid H265 data length. Length: ${data.size}")
            return -1
        }

        if (DEBUG) {
            LogContext.log.d(
                TAG,
                "Frame HEX data[0~4]=${data[0].toHexString()},${data[1].toHexString()},${data[2].toHexString()},${data[3].toHexString()},${data[4].toHexString()}"
            )
        }
        return if (data[0].toInt() != 0x0 || data[1].toInt() != 0x0 && data[2].toInt() != 0x0 || data[3].toInt() != 0x1) {
            LogContext.log.d(TAG, "Not valid H265 data.")
            -1
        } else {
            val nalu = data[4].toInt()
            ((nalu and 0x7E) shr 1)
        }
    }

    fun getNaluType(naluByte: Byte) = (naluByte.toInt() and 0x7E) shr 1

    @Suppress("unused")
    fun getNaluTypeInStr(naluType: Int): String {
        return when (naluType) {
            NALU_TYPE_VPS -> "VPS"
            NALU_TYPE_SPS -> "SPS"
            NALU_TYPE_PPS -> "PPS"
            NALU_TYPE_IDR -> "I"
            NALU_TYPE_SEI -> "SEI"
            else -> "Unknown"
        }
    }

    fun getNaluTypeInStr(data: ByteArray) = getNaluTypeInStr(getNaluType(data))
}