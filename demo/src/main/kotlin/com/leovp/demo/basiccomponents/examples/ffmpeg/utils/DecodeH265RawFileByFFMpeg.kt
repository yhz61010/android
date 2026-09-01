package com.leovp.demo.basiccomponents.examples.ffmpeg.utils

import android.os.SystemClock
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.ffmpeg.video.H264HevcDecoder
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.opengl.BaseRenderer
import com.leovp.opengl.ui.LeoGLSurfaceView
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

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
 * According to some articles(Not verified), NALU type from 0~9 indicates P frame, 16~21 indicates I
 * frame.
 *
 * Author: Michael Leo
 * Date: 20-7-30 上午10:54
 */
class DecodeH265RawFileByFFMpeg {
    companion object {
        private const val TAG = "FFMpegH265"
        private const val FRAME_INTERVAL_MS = 1_000L / 30
        private const val H265_NAL_VPS = 32
        private const val H265_NAL_SPS = 33
        private const val H265_NAL_PPS = 34
        private const val H265_NAL_PREFIX_SEI = 39
        private const val H265_NAL_SUFFIX_SEI = 40
        private const val H265_NAL_VCL_MIN = 0
        private const val H265_NAL_VCL_MAX = 31
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var decodeJob: Job? = null
    private val resourcesClosed = AtomicBoolean(false)
    private lateinit var glSurfaceView: LeoGLSurfaceView

    private lateinit var videoInfo: H264HevcDecoder.DecodeVideoInfo
    private lateinit var nalUnitReader: AnnexBNalUnitReader
    private var renderWidth = 0
    private var renderHeight = 0
    private var configuredVideoWidth = 0
    private var configuredVideoHeight = 0

    private val videoDecoder = H264HevcDecoder()

    @Volatile
    private var isDecoding = false

    @Volatile
    private var isClosed = false

    fun init(videoFile: String, glSurfaceView: LeoGLSurfaceView) {
        this.glSurfaceView = glSurfaceView
        val file = File(videoFile)
        LogContext.log.w(TAG, "File length=${file.length()}")
        nalUnitReader = AnnexBNalUnitReader(file.inputStream())

        var vps: ByteArray? = null
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        while (vps == null || sps == null || pps == null) {
            val nalUnit =
                checkNotNull(nalUnitReader.nextNalUnit()) {
                    "H.265 stream ended before VPS, SPS, and PPS were found."
                }
            when (h265NalUnitType(nalUnit)) {
                H265_NAL_VPS -> if (vps == null) vps = nalUnit
                H265_NAL_SPS -> if (sps == null) sps = nalUnit
                H265_NAL_PPS -> if (pps == null) pps = nalUnit
            }
        }

        val vpsBytes = checkNotNull(vps)
        val spsBytes = checkNotNull(sps)
        val ppsBytes = checkNotNull(pps)
        LogContext.log.w(
            TAG,
            "vps[${vpsBytes.size}], sps[${spsBytes.size}], pps[${ppsBytes.size}]"
        )

        videoInfo = initDecoder(vpsBytes, spsBytes, ppsBytes, null, null)
        val renderSize = glSurfaceView.context.screenAvailableResolution
        renderWidth = renderSize.width
        renderHeight = renderSize.height
        updateVideoDimension(videoInfo.width, videoInfo.height)
    }

    private fun initDecoder(
        vps: ByteArray?,
        sps: ByteArray,
        pps: ByteArray,
        prefixSei: ByteArray?,
        suffixSei: ByteArray?,
    ): H264HevcDecoder.DecodeVideoInfo {
        val videoInfo: H264HevcDecoder.DecodeVideoInfo =
            videoDecoder.init(vps, sps, pps, prefixSei, suffixSei)
        LogContext.log.w(TAG, "Decoded videoInfo=${videoInfo.toJsonString()}")
        return videoInfo
    }

    private fun decodeVideo(rawVideo: ByteArray): List<H264HevcDecoder.DecodedVideoFrame> =
        videoDecoder.decodeFrames(rawVideo)

    private fun renderFrame(frame: H264HevcDecoder.DecodedVideoFrame) {
        updateVideoDimension(frame.width, frame.height)
        val yuv420Type = if (videoInfo.pixelFormatId < 0) {
            BaseRenderer.Yuv420Type.I420
        } else {
            BaseRenderer.Yuv420Type.getType(videoInfo.pixelFormatId)
        }
        glSurfaceView.render(frame.yuvOrRgbBytes, yuv420Type)
    }

    private fun updateVideoDimension(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == configuredVideoWidth && height == configuredVideoHeight) return
        glSurfaceView.setVideoDimension(width, height, renderWidth, renderHeight)
        configuredVideoWidth = width
        configuredVideoHeight = height
    }

    fun close() {
        LogContext.log.d(TAG, "close()")
        if (isClosed) return
        isClosed = true
        isDecoding = false

        val runningJob = decodeJob
        if (runningJob == null) {
            releaseResources()
            ioScope.cancel()
            return
        }
        runningJob.invokeOnCompletion {
            releaseResources()
            ioScope.cancel()
        }
        runningJob.cancel()
    }

    private fun releaseResources() {
        if (!resourcesClosed.compareAndSet(false, true)) return
        runCatching { videoDecoder.close() }
            .onFailure { LogContext.log.e(TAG, "Error releasing decoder", it) }
        if (::nalUnitReader.isInitialized) {
            runCatching { nalUnitReader.close() }
                .onFailure { LogContext.log.e(TAG, "Error closing input file", it) }
        }
    }

    fun startDecoding() {
        // FIXME
        // If you use coroutines here, the video will be displayed. I don't know why!!!
        isDecoding = true
        if (isClosed || decodeJob?.isActive == true) return
        decodeJob = ioScope.launch {
            runCatching {
                var reachedEndOfFile = false
                while (isDecoding && !isClosed) {
                    ensureActive()
                    val nalUnitGroup =
                        nalUnitReader.nextNalUnitGroupEndingWith {
                            h265NalUnitType(it) in H265_NAL_VCL_MIN..H265_NAL_VCL_MAX
                        }
                    if (nalUnitGroup == null) {
                        reachedEndOfFile = true
                        break
                    }
                    decodeAndRender(
                        nalUnitGroup.bytes,
                        h265NalUnitType(nalUnitGroup.endingNalUnit)
                    )
                }
                if (reachedEndOfFile && !isClosed) {
                    videoDecoder.drain().forEach(::renderFrame)
                }
            }.onFailure {
                if (it !is CancellationException) {
                    LogContext.log.e(TAG, "Decoding failed", it)
                }
            }
        }
    }

    private fun decodeAndRender(encodedPacket: ByteArray, nalType: Int) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        var endNs = startNs
        try {
            if (isClosed) return
            val decodedFrames = decodeVideo(encodedPacket)
            val decodedNs = SystemClock.elapsedRealtimeNanos()
            decodedFrames.forEach(::renderFrame)
            endNs = SystemClock.elapsedRealtimeNanos()
            val nalTypeName =
                when (nalType) {
                    H265_NAL_VPS -> "VPS"
                    H265_NAL_SPS -> "SPS"
                    H265_NAL_PPS -> "PPS"
                    H265_NAL_PREFIX_SEI -> "PREFIX_SEI"
                    H265_NAL_SUFFIX_SEI -> "SUFFIX_SEI"
                    in 16..21 -> "IDR"
                    in 0..31 -> "VCL"
                    else -> nalType.toString()
                }
            val lastFrame = decodedFrames.lastOrNull()
            LogContext.log.w(
                TAG,
                "frame[$nalTypeName][${encodedPacket.size}][decode " +
                    "cost=${(decodedNs - startNs) / 1_000_000}ms]" +
                    "[render cost=${(endNs - decodedNs) / 1_000}us] " +
                    "outputs=${decodedFrames.size} ${lastFrame?.width}x${lastFrame?.height}"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            endNs = SystemClock.elapsedRealtimeNanos()
            LogContext.log.e(TAG, "decode error.", e)
        }

        val sleepOffset = FRAME_INTERVAL_MS - (endNs - startNs) / 1_000_000
        if (sleepOffset > 0 && !isClosed) Thread.sleep(sleepOffset)
    }
}
