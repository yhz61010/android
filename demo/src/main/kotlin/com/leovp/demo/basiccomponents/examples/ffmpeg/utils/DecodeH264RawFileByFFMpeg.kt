package com.leovp.demo.basiccomponents.examples.ffmpeg.utils

import android.os.SystemClock
import android.util.Size
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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-7-30 上午10:54
 */
class DecodeH264RawFileByFFMpeg {
    companion object {
        private const val TAG = "FFMpegH264"
        private const val FRAME_INTERVAL_MS = 1_000L / 30
        private const val H264_NAL_NON_IDR = 1
        private const val H264_NAL_IDR = 5
        private const val H264_NAL_SEI = 6
        private const val H264_NAL_SPS = 7
        private const val H264_NAL_PPS = 8
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var decodeJob: Job? = null
    private val resourcesClosed = AtomicBoolean(false)
    private lateinit var glSurfaceView: LeoGLSurfaceView

    private lateinit var videoInfo: H264HevcDecoder.DecodeVideoInfo
    private lateinit var nalUnitReader: AnnexBNalUnitReader
    private var renderWidth = 0
    private var renderHeight = 0

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

        var sps: ByteArray? = null
        var pps: ByteArray? = null
        while (sps == null || pps == null) {
            val nalUnit =
                checkNotNull(nalUnitReader.nextNalUnit()) {
                    "H.264 stream ended before SPS and PPS were found."
                }
            when (h264NalUnitType(nalUnit)) {
                H264_NAL_SPS -> if (sps == null) sps = nalUnit
                H264_NAL_PPS -> if (pps == null) pps = nalUnit
            }
        }

        val spsBytes = checkNotNull(sps)
        val ppsBytes = checkNotNull(pps)
        LogContext.log.w(TAG, "sps[${spsBytes.size}], pps[${ppsBytes.size}]")

        videoInfo = initDecoder(spsBytes, ppsBytes)
        val renderSize = glSurfaceView.context.screenAvailableResolution
        renderWidth = renderSize.width
        renderHeight = renderSize.height
    }

    private fun initDecoder(sps: ByteArray, pps: ByteArray): H264HevcDecoder.DecodeVideoInfo {
        val videoInfo: H264HevcDecoder.DecodeVideoInfo =
            videoDecoder.init(null, sps, pps, null, null, H264HevcDecoder.RgbType.AV_PIX_FMT_NONE)
        LogContext.log.w(TAG, "Decoded videoInfo=${videoInfo.toJsonString()}")
        return videoInfo
    }

    private fun decodeVideo(rawVideo: ByteArray): List<H264HevcDecoder.DecodedVideoFrame> =
        videoDecoder.decodeFrames(rawVideo)

    private suspend fun renderFrame(frame: H264HevcDecoder.DecodedVideoFrame) {
        val startedAt = SystemClock.elapsedRealtime()
        val yuv420Type = if (videoInfo.pixelFormatId < 0) {
            BaseRenderer.Yuv420Type.I420
        } else {
            BaseRenderer.Yuv420Type.getType(videoInfo.pixelFormatId)
        }
        glSurfaceView.renderAndAwait(
            frame.yuvOrRgbBytes,
            Size(frame.width, frame.height),
            Size(renderWidth, renderHeight),
            yuv420Type
        )
        val remaining = FRAME_INTERVAL_MS - (SystemClock.elapsedRealtime() - startedAt)
        if (remaining > 0) delay(remaining.milliseconds)
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
                            h264NalUnitType(it) in H264_NAL_NON_IDR..H264_NAL_IDR
                        }
                    if (nalUnitGroup == null) {
                        reachedEndOfFile = true
                        break
                    }
                    decodeAndRender(
                        nalUnitGroup.bytes,
                        h264NalUnitType(nalUnitGroup.endingNalUnit)
                    )
                }
                if (reachedEndOfFile && !isClosed) {
                    videoDecoder.drain().forEach { renderFrame(it) }
                }
            }.onFailure {
                if (it !is CancellationException) {
                    LogContext.log.e(TAG, "Decoding failed", it)
                }
            }
        }
    }

    private suspend fun decodeAndRender(encodedPacket: ByteArray, nalType: Int) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        try {
            if (isClosed) return
            val decodedFrames = decodeVideo(encodedPacket)
            val decodedNs = SystemClock.elapsedRealtimeNanos()
            decodedFrames.forEach { renderFrame(it) }
            val endNs = SystemClock.elapsedRealtimeNanos()
            val nalTypeName =
                when (nalType) {
                    H264_NAL_SPS -> "SPS"
                    H264_NAL_PPS -> "PPS"
                    H264_NAL_IDR -> "IDR"
                    H264_NAL_NON_IDR -> "P"
                    H264_NAL_SEI -> "SEI"
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
            LogContext.log.e(TAG, "decode error.", e)
        }
    }
}
