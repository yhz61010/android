@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.media

import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.leovp.lib_bytes.toHexString
import com.leovp.log_sdk.LogContext

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
 * 00 00 00 01 40 01  nal_unit_type 值为 32，VPS 语义为视频参数集 (Video Parameter Set)
 * 00 00 00 01 42 01  nal_unit_type 值为 33，SPS 语义为序列参数集 (Sequence Parameter Set)
 * 00 00 00 01 44 01  nal_unit_type 值为 34，PPS 语义为图像参数集 (Picture Parameter Set)
 * 00 00 00 01 4E 01  nal_unit_type 值为 39，SEI 语义为补充增强信息 (Supplemental Enhancement Information)
 *
 * NALU values in the range 16(inclusive) to 23(inclusive) are all Key Frames, AKA I Frame.
 *                    nal_unit_type 值为 16, BLA_W_LP 属于 IRAP picture (Check comments below)
 *                    nal_unit_type 值为 17, BLA_W_RADL 属于 IRAP picture (Check comments below)
 *                    nal_unit_type 值为 18, BLA_N_LP 属于 IRAP picture (Check comments below)
 * 00 00 00 01 26 01  nal_unit_type 值为 19，IDR_W_RADL 属于 IDR picture 语义为可能有 RADL 图像的 IDR 图像的 SS(Slice Segment) 编码数据
 * 00 00 00 01 28 01  nal_unit_type 值为 20，IDR_N_LP 属于 IDR picture
 *                    nal_unit_type 值为 21, CRA_NUT 属于 RAP picture
 *
 * 00 00 00 01 02 01  nal_unit_type 值为 1，P 语义为被参考的后置图像，且非 TSA、非 STSA 的 SS(Slice Segment) 编码数据
 * ```
 *
 * According to some articles, NALU type from 0~9 indicates P frame(NOT verified), 16~23 indicates I frame(verified).
 *
 * BLA access unit: An access unit in which the coded picture is a BLA picture.
 * BLA picture: An IRAP picture for which each VCL NAL unit has nal_unit_type equal to BLA_W_LP, BLA_W_RADL, or BLA_N_LP.
 *
 * Informative note: An IRAP access unit may be an IDR access unit, a BLA access unit, or a CRA access unit.
 *
 * CRA access unit: An access unit in which the coded picture is a CRA picture.
 * CRA picture: A RAP picture for which each VCL NAL unit has nal_unit_type equal to CRA_NUT.
 * IDR access unit: An access unit in which the coded picture is an IDR picture.
 * IDR picture: A RAP picture for which each VCL NAL unit has nal_unit_type equal to IDR_W_RADL or IDR_N_LP.
 * IRAP access unit: An access unit in which the coded picture is an IRAP picture.
 * IRAP picture: A coded picture for which each VCL NAL unit has nal_unit_type in the range of BLA_W_LP (16) to RSV_IRAP_VCL23 (23), inclusive.
 * [Reference](https://datatracker.ietf.org/doc/html/rfc7798#page-16)
 *
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
 * https://blog.csdn.net/DittyChen/article/details/87798547
 *
 * Author: Michael Leo
 * Date: 2021/5/13 3:21 PM
 */
object H265Util {
    private const val TAG = "H265Util"
    private const val DEBUG = false

    const val NALU_TRAIL_N = 0
    const val NALU_TRAIL_R = 1
    const val NALU_TSA_N = 2
    const val NALU_TSA_R = 3
    const val NALU_STSA_N = 4
    const val NALU_STSA_R = 5
    const val NALU_RADL_N = 6
    const val NALU_RADL_R = 7
    const val NALU_RASL_N = 8
    const val NALU_RASL_R = 9

    const val NALU_TYPE_VPS = 32 // 0x20
    const val NALU_TYPE_SPS = 33 // 0x21
    const val NALU_TYPE_PPS = 34 // 0x22
    const val NALU_TYPE_PREFIX_SEI = 39 // 0x27
    const val NALU_TYPE_SUFFIX_SEI = 40 // 0x28

    const val NALU_TYPE_BLA_W_LP = 16 // 0x10
    const val NALU_TYPE_BLA_W_RADL = 17 // 0x11
    const val NALU_TYPE_BLA_N_LP = 18 // 0x12
    const val NALU_TYPE_IDR_W_RADL = 19 // 0x13
    const val NALU_TYPE_IDR_N_LP = 20 // 0x14
    const val NALU_TYPE_CRA_NUT = 21 // 0x15 // 22(RSV_IRAP_VCL22) and 23(RSV_IRAP_VCL23) Reserved IRAP VCL NAL unit types

    fun isIdrFrame(data: ByteArray): Boolean {
        return (16..23).contains(getNaluType(data))
    }

    fun isPFrame(data: ByteArray): Boolean {
        return (0..9).contains(getNaluType(data))
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

    fun isSei(data: ByteArray): Boolean {
        val naluType: Int = getNaluType(data)
        return NALU_TYPE_PREFIX_SEI == naluType /* 39 */ || NALU_TYPE_SUFFIX_SEI == naluType /* 40 */
    }

    fun isPrefixSei(data: ByteArray): Boolean {
        val naluType: Int = getNaluType(data)
        return NALU_TYPE_PREFIX_SEI == naluType /* 39 */
    }

    fun isSuffixSei(data: ByteArray): Boolean {
        val naluType: Int = getNaluType(data)
        return NALU_TYPE_SUFFIX_SEI == naluType /* 40 */
    }

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     * 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
     * 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
     * 0,0,0,1,44,1,C0,66,3C,E,C6,40,
     * 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
     * 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
     *
     * @return The returned vps data contains the delimiter prefix 0,0,0,1
     */
    fun getVps(data: ByteArray): ByteArray? {
        return if (!isVps(data)) {
            null
        } else try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            // 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
            // 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
            // 0,0,0,1,44,1,C0,66,3C,E,C6,40,
            // 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
            // 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
            for (i in 4 until data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    val vps = ByteArray(i)
                    System.arraycopy(data, 0, vps, 0, i)
                    return vps
                }
            }
            data
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getVps error msg=${e.message}")
            null
        }
    }

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     * 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
     * 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
     * 0,0,0,1,44,1,C0,66,3C,E,C6,40,
     * 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
     * 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
     *
     * @return The returned sps data contains the delimiter prefix 0,0,0,1
     */
    fun getSps(data: ByteArray): ByteArray? {
        if (!CodecUtil.findStartCode(data)) return null
        return try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            // 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
            // 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
            // 0,0,0,1,44,1,C0,66,3C,E,C6,40,
            // 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
            // 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
            var startIndex = 0
            for (i in 3 until data.size) {
                if (CodecUtil.findStartCode(data, i)) {
                    val spsLength = i - startIndex
                    val sps = ByteArray(spsLength)
                    System.arraycopy(data, startIndex, sps, 0, spsLength)
                    if (isSps(sps)) return sps else startIndex = i
                }
            }
            if (startIndex > 0) {
                val spsLength = data.size - startIndex
                val sps = ByteArray(spsLength)
                System.arraycopy(data, startIndex, sps, 0, spsLength)
                if (isSps(sps)) return sps
            }
            if (isSps(data)) data else null
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getSps error msg=${e.message}")
            null
        }
    }

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     * 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
     * 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
     * 0,0,0,1,44,1,C0,66,3C,E,C6,40
     * 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
     * 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
     *
     * @return The returned pps data contains the delimiter prefix 0,0,0,1
     */
    fun getPps(data: ByteArray): ByteArray? {
        if (!CodecUtil.findStartCode(data)) return null
        return try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            // 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
            // 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
            // 0,0,0,1,44,1,C0,66,3C,E,C6,40,
            // 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
            // 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
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

    /**
     * @param data The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
     * Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
     * 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
     * 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
     * 0,0,0,1,44,1,C0,66,3C,E,C6,40
     * 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
     * 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
     *
     * @return The returned sei data contains the delimiter prefix 0,0,0,1
     */
    fun getSei(data: ByteArray): ByteArray? {
        if (!CodecUtil.findStartCode(data)) return null
        return try {
            // The following example contains NALU_TYPE_VPS, NALU_TYPE_SPS and NALU_TYPE_PPS(All data are in hexadecimal)
            // Example: 0,0,0,1,0x40,x,x,x,0,0,0,1,0x42,x,x,x,0,0,0,1,0x44,x,x,x,0,0,0,1,x,x,x
            // 0,0,0,1,40,1,C,1,FF,FF,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,2C,9,
            // 0,0,0,1,42,1,1,1,60,0,0,3,0,0,3,0,0,3,0,0,3,0,78,A0,4,62,0,FC,7C,BA,2D,24,B0,4B,B2,
            // 0,0,0,1,44,1,C0,66,3C,E,C6,40,
            // 0,0,0,1,4E,1,5,1A,47,56,4A,DC,5C,4C,43,3F,94,EF,C5,11,3C,D1,43,A8,3,EE,0,0,EE,2,0,2D,C6,C0,80,
            // 0,0,0,1,28,1,AF,78,CD,3B,31,6,1E,D,6E,C,54,39,B4,3F,C0,9B,EA,7E,28,E6,81,6,7,CF,3F,B6,EA,E0,90,39,69,B4,B4,80,12,5E,C9,D
            var startIndex = 0
            for (i in 3 until data.size) {
                if (CodecUtil.findStartCode(data, i)) {
                    val seiLength = i - startIndex
                    val sei = ByteArray(seiLength)
                    System.arraycopy(data, startIndex, sei, 0, seiLength)
                    if (isSei(sei)) return sei else startIndex = i
                }
            }
            if (startIndex > 0) {
                val seiLength = data.size - startIndex
                val sei = ByteArray(seiLength)
                System.arraycopy(data, startIndex, sei, 0, seiLength)
                if (isSei(sei)) return sei
            }
            if (isSei(data)) data else null
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getSei error msg=${e.message}")
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
                TAG, "Frame HEX data[0~4]=${data[0].toHexString()},${data[1].toHexString()},${data[2].toHexString()},${data[3].toHexString()},${data[4].toHexString()}"
            )
        }
        return if (data[0].toInt() != 0x0 || data[1].toInt() != 0x0 && data[2].toInt() != 0x0 || data[3].toInt() != 0x1) {
            if (DEBUG) LogContext.log.d(TAG, "Not valid H265 data.")
            -1
        } else {
            val nalu = data[4].toInt()
            ((nalu and 0x7E) shr 1)
        }
    }

    fun getNaluType(naluByte: Byte) = (naluByte.toInt() and 0x7E) shr 1

    @Suppress("unused")
    fun getNaluTypeName(naluType: Int): String {
        return when (naluType) {
            NALU_TYPE_VPS        -> "VPS" // 32
            NALU_TYPE_SPS        -> "SPS" // 33
            NALU_TYPE_PPS        -> "PPS" // 34
            NALU_TYPE_PREFIX_SEI -> "PREFIX_SEI" // 39
            NALU_TYPE_SUFFIX_SEI -> "SUFFIX_SEI" // 40

            // NALU values in the range 16(inclusive) to 23(inclusive) are all Key Frames, AKA I Frame.
            NALU_TYPE_BLA_W_LP   -> "I_BLA_W_LP" // 16
            NALU_TYPE_BLA_W_RADL -> "I_BLA_W_RADL" // 17
            NALU_TYPE_BLA_N_LP   -> "I_BLA_N_LP" // 18
            NALU_TYPE_IDR_W_RADL -> "IDR_W_RADL" // 19
            NALU_TYPE_IDR_N_LP   -> "IDR_N_LP" // 20
            NALU_TYPE_CRA_NUT    -> "I_CRA_NUT" // 21

            NALU_TRAIL_N         -> "P_TRAIL_N" // 0
            NALU_TRAIL_R         -> "P_TRAIL_R" // 1
            NALU_TSA_N           -> "P_TSA_N" // 2
            NALU_TSA_R           -> "P_TSA_R" // 3
            NALU_STSA_N          -> "P_STSA_N" // 4
            NALU_STSA_R          -> "P_STSA_R" // 5
            NALU_RADL_N          -> "P_RADL_N" // 6
            NALU_RADL_R          -> "P_RADL_R" // 7
            NALU_RASL_N          -> "P_RASL_N" // 8
            NALU_RASL_R          -> "P_RASL_R" // 9

            else                 -> "Unknown"
        }
    }

    fun getNaluTypeName(data: ByteArray): String = getNaluTypeName(getNaluType(data))

    fun getNaluTypeSimpleName(data: ByteArray): String {
        if (isIdrFrame(data)) return "I"
        if (isPFrame(data)) return "P"
        if (isSei(data)) return "SEI"
        return getNaluTypeName(data)
    }

    fun getHevcCodec(encoder: Boolean = true): List<MediaCodecInfo> = CodecUtil.getCodecListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC, encoder)
}