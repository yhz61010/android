package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.utils

import android.os.SystemClock
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.exts.kotlin.truncate
import com.leovp.ffmpeg.video.H264HevcDecoder
import com.leovp.lib_bytes.toHexStringLE
import com.leovp.log_sdk.LogContext
import com.leovp.opengl_sdk.ui.LeoGLSurfaceView
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile


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
 * Date: 20-7-30 上午10:54
 */
class DecodeH265RawFileByFFMpeg {
    companion object {
        private const val TAG = "FFMpegH265"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var glSurfaceView: LeoGLSurfaceView

    private lateinit var videoInfo: H264HevcDecoder.DecodeVideoInfo
    private var csd0Size: Int = 0

    private val videoDecoder = H264HevcDecoder()

    fun init(videoFile: String, glSurfaceView: LeoGLSurfaceView) {
        this.glSurfaceView = glSurfaceView
        rf = RandomAccessFile(File(videoFile), "r")
        LogContext.log.w(TAG, "File length=${rf.length()}")

        val vps = getNalu()!!
        val sps = getNalu()!!
        val pps = getNalu()!!
        val psei = getNalu()!!
        val ssei = getNalu()!!

        LogContext.log.w(TAG, "vps[${vps.size}]=${vps.toHexStringLE()}")
        LogContext.log.w(TAG, "sps[${sps.size}]=${sps.toHexStringLE()}")
        LogContext.log.w(TAG, "pps[${pps.size}]=${pps.toHexStringLE()}")
        LogContext.log.w(TAG, "prefix_sei[${psei.size}]=${psei.toHexStringLE().truncate(80)}")
        LogContext.log.w(TAG, "suffix_sei[${ssei.size}]=${ssei.toHexStringLE().truncate(80)}")

        val csd0 = vps + sps + pps + psei + ssei
        LogContext.log.w(TAG, "csd0[${csd0.size}]=${csd0.toHexStringLE().truncate(180)}")
        csd0Size = csd0.size
        currentIndex = csd0Size.toLong()


        videoInfo = initDecoder(vps, sps, pps, psei, ssei)
        glSurfaceView.setVideoDimension(videoInfo.width, videoInfo.height)
        decodeVideo(csd0)
    }

    private fun initDecoder(vps: ByteArray?, sps: ByteArray, pps: ByteArray, prefixSei: ByteArray?, suffixSei: ByteArray?): H264HevcDecoder.DecodeVideoInfo {
        val videoInfo: H264HevcDecoder.DecodeVideoInfo = videoDecoder.init(vps, sps, pps, prefixSei, suffixSei)
        LogContext.log.w(TAG, "Decoded videoInfo=${videoInfo.toJsonString()}")
        return videoInfo
    }

    private fun decodeVideo(rawVideo: ByteArray): H264HevcDecoder.DecodedVideoFrame? = videoDecoder.decode(rawVideo)

    private lateinit var rf: RandomAccessFile

    private var currentIndex = 0L
    private fun getRawH265(bufferSize: Int = 1_000_000): ByteArray? {
        val bb = ByteArray(bufferSize)
        //        LogContext.log.w(TAG, "Current file pos=$currentIndex")
        rf.seek(currentIndex)
        var readSize = rf.read(bb, 0, bufferSize)
        if (readSize == -1) {
            return null
        }
        for (i in 4 until readSize) {
            if (findStartCode4(bb, readSize - i)) {
                readSize -= i
                break
            }
        }
        val wholeNalu = ByteArray(readSize)
        System.arraycopy(bb, 0, wholeNalu, 0, readSize)
        currentIndex += readSize
        return wholeNalu
    }

    private fun getNalu(): ByteArray? {
        var curIndex = 0
        val bb = ByteArray(800_000)
        rf.read(bb, curIndex, 4)
        if (findStartCode4(bb, 0)) {
            curIndex = 4
        }
        var findNALStartCode = false
        var nextNalStartPos = 0
        var reWind = 0
        while (!findNALStartCode) {
            val hex = rf.read()
            //            val naluType = getNaluType(hex.toByte())
            //                LogContext.log.w(TAG, "NALU Type=$naluType")
            if (curIndex >= bb.size) {
                return null
            }
            bb[curIndex++] = hex.toByte()
            if (hex == -1) {
                nextNalStartPos = curIndex
            }
            if (findStartCode4(bb, curIndex - 4)) {
                findNALStartCode = true
                reWind = 4
                nextNalStartPos = curIndex - reWind
            }
        }
        val nal = ByteArray(nextNalStartPos)
        System.arraycopy(bb, 0, nal, 0, nextNalStartPos)
        val pos = rf.filePointer
        val setPos = pos - reWind
        rf.seek(setPos)
        return nal
    }

    // Find NALU prefix "00 00 00 01"
    private fun findStartCode4(bb: ByteArray, offSet: Int): Boolean {
        if (offSet < 0) {
            return false
        }
        return bb[offSet].toInt() == 0 && bb[offSet + 1].toInt() == 0 && bb[offSet + 2].toInt() == 0 && bb[offSet + 3].toInt() == 1
    }

    fun close() {
        LogContext.log.d(TAG, "close()")
        videoDecoder.release()
        ioScope.cancel()
    }

    fun startDecoding() {
        // FIXME
        // If use coroutines here, the video will be displayed. I don't know why!!!
        ioScope.launch {
            val startIdx = 4
            runCatching {
                while (true) {
                    ensureActive()
                    val bytes = getRawH265() ?: break
                    var previousStart = 0
                    for (i in startIdx until bytes.size) {
                        ensureActive()
                        if (findStartCode4(bytes, i)) {
                            val frame = ByteArray(i - previousStart)
                            System.arraycopy(bytes, previousStart, frame, 0, frame.size)

                            val st1 = SystemClock.elapsedRealtime()
                            var st3: Long
                            try {
                                val decodeFrame: H264HevcDecoder.DecodedVideoFrame? = decodeVideo(frame)
                                val st2 = SystemClock.elapsedRealtimeNanos()
                                decodeFrame?.let {
                                    val yuv420Type =
                                            if (videoInfo.pixelFormatId < 0) com.leovp.opengl_sdk.GLRenderer.Yuv420Type.I420 else com.leovp.opengl_sdk.GLRenderer.Yuv420Type.getType(
                                                videoInfo.pixelFormatId)
                                    glSurfaceView.render(it.yuvBytes, yuv420Type)
                                }
                                st3 = SystemClock.elapsedRealtimeNanos()
                                LogContext.log.w(TAG,
                                    "frame[${frame.size}][decode cost=${st2 / 1000_000 - st1}ms][render cost=${(st3 - st2) / 1000}us] ${decodeFrame?.width}x${decodeFrame?.height}")
                            } catch (e: Exception) {
                                st3 = SystemClock.elapsedRealtimeNanos()
                                LogContext.log.e(TAG, "decode error.", e)
                            }

                            previousStart = i
                            // FIXME We'd better control the FPS by SpeedManager
                            val sleepOffset: Long = 1000 / 30 - (st3 / 1000_000 - st1)
                            Thread.sleep(if (sleepOffset < 0) 0 else sleepOffset)
                        }
                    }
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    @Suppress("unused")
    private fun getNaluType(nalu: Byte): Int = ((nalu.toInt() and 0x07E) shr 1)
}