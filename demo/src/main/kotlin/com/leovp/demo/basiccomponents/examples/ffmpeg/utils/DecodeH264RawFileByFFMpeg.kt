package com.leovp.demo.basiccomponents.examples.ffmpeg.utils

import android.os.SystemClock
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.androidbase.exts.kotlin.truncate
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.bytes.toHexString
import com.leovp.ffmpeg.video.H264HevcDecoder
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.opengl.BaseRenderer
import com.leovp.opengl.ui.LeoGLSurfaceView
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-7-30 上午10:54
 */
class DecodeH264RawFileByFFMpeg {
    companion object {
        private const val TAG = "FFMpegH264"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var decodeJob: Job? = null
    private val resourcesClosed = AtomicBoolean(false)
    private lateinit var glSurfaceView: LeoGLSurfaceView

    private lateinit var videoInfo: H264HevcDecoder.DecodeVideoInfo
    private var csd0Size: Int = 0

    private val videoDecoder = H264HevcDecoder()

    @Volatile
    private var isDecoding = false

    @Volatile
    private var isClosed = false

    fun init(videoFile: String, glSurfaceView: LeoGLSurfaceView) {
        this.glSurfaceView = glSurfaceView
        rf = RandomAccessFile(File(videoFile), "r")
        LogContext.log.w(TAG, "File length=${rf.length()}")

        val sps = getNalu()!!
        val pps = getNalu()!!

        LogContext.log.w(TAG, "sps[${sps.size}]=${sps.toHexString()}")
        LogContext.log.w(TAG, "pps[${pps.size}]=${pps.toHexString()}")

        val csd0 = sps + pps
        LogContext.log.w(TAG, "csd0[${csd0.size}]=${csd0.toHexString().truncate(180)}")
        csd0Size = csd0.size
        currentIndex = csd0Size.toLong()

        videoInfo = initDecoder(sps, pps)
        //            glSurfaceView.setVideoDimension(videoInfo.width, videoInfo.height)
        val renderSize = glSurfaceView.context.screenAvailableResolution
        glSurfaceView.setVideoDimension(1920, 800, renderSize.width, renderSize.height)
        decodeVideo(csd0)
    }

    private fun initDecoder(sps: ByteArray, pps: ByteArray): H264HevcDecoder.DecodeVideoInfo {
        val videoInfo: H264HevcDecoder.DecodeVideoInfo =
            videoDecoder.init(null, sps, pps, null, null, H264HevcDecoder.RgbType.AV_PIX_FMT_NONE)
        LogContext.log.w(TAG, "Decoded videoInfo=${videoInfo.toJsonString()}")
        return videoInfo
    }

    private fun decodeVideo(rawVideo: ByteArray): H264HevcDecoder.DecodedVideoFrame? =
        videoDecoder.decode(rawVideo)

    private fun renderFrame(frame: H264HevcDecoder.DecodedVideoFrame) {
        val yuv420Type = if (videoInfo.pixelFormatId < 0) {
            BaseRenderer.Yuv420Type.I420
        } else {
            BaseRenderer.Yuv420Type.getType(videoInfo.pixelFormatId)
        }
        glSurfaceView.render(frame.yuvOrRgbBytes, yuv420Type)
    }

    private lateinit var rf: RandomAccessFile

    private var currentIndex = 0L
    private fun getRawH264(bufferSize: Int = 1_500_000): ByteArray? {
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
        return bb[offSet].toInt() == 0 &&
            bb[offSet + 1].toInt() == 0 &&
            bb[offSet + 2].toInt() == 0 &&
            bb[offSet + 3].toInt() == 1
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
        if (::rf.isInitialized) {
            runCatching { rf.close() }
                .onFailure { LogContext.log.e(TAG, "Error closing input file", it) }
        }
    }

    fun startDecoding() {
        // FIXME
        // If you use coroutines here, the video will be displayed. I don't know why!!!
        isDecoding = true
        if (isClosed || decodeJob?.isActive == true) return
        decodeJob = ioScope.launch {
            val startIdx = 4
            runCatching {
                var reachedEndOfFile = false
                while (isDecoding && !isClosed) {
                    ensureActive()
                    val bytes = getRawH264()
                    if (bytes == null) {
                        reachedEndOfFile = true
                        break
                    }
                    var previousStart = 0
                    for (i in startIdx until bytes.size) {
                        // Check if we should stop decoding
                        if (!isDecoding || isClosed) {
                            break
                        }
                        ensureActive()
                        if (findStartCode4(bytes, i)) {
                            val frame = ByteArray(i - previousStart)
                            System.arraycopy(bytes, previousStart, frame, 0, frame.size)

                            val st1 = SystemClock.elapsedRealtime()
                            var st3: Long
                            try {
                                // Don't decode if already closed
                                if (!isClosed) {
                                    val decodeFrame: H264HevcDecoder.DecodedVideoFrame? =
                                        decodeVideo(frame)
                                    val st2 = SystemClock.elapsedRealtimeNanos()
                                    decodeFrame?.let { decodedFrame ->
                                        renderFrame(decodedFrame)

                                        // it.yuvOrRgbBytes.toBitmapFromBytes(it.width,
                                        // it.height)?.writeToFile(File("/sdcard/yuv2bgr/argb32-$i.b
                                        // mp"))
                                    }
                                    st3 = SystemClock.elapsedRealtimeNanos()
                                    val naluType = when {
                                        H264Util.isSps(frame) -> "SPS"
                                        H264Util.isPps(frame) -> "PPS"
                                        H264Util.isIdrFrame(frame) -> "IDR"
                                        H264Util.isNoneIdrFrame(frame) -> ""
                                        else -> H264Util.getNaluType(frame).toString()
                                    }
                                    LogContext.log.w(
                                        TAG,
                                        "frame" +
                                            (if (naluType.isNotEmpty()) "[$naluType]" else "") +
                                            "[${frame.size}][decode " +
                                            "cost=${st2 / 1000_000 - st1}ms]" +
                                            "[render cost=${(st3 - st2) / 1000}us] " +
                                            "${decodeFrame?.width}x${decodeFrame?.height}"
                                    )
                                } else {
                                    // If closed, still record time for sleep calculation
                                    st3 = SystemClock.elapsedRealtimeNanos()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                st3 = SystemClock.elapsedRealtimeNanos()
                                LogContext.log.e(TAG, "decode error.", e)
                            }

                            previousStart = i
                            // FIXME We'd better control the FPS by SpeedManager
                            val sleepOffset: Long = 1000 / 30 - (st3 / 1000_000 - st1)
                            if (sleepOffset > 0 && !isClosed) {
                                Thread.sleep(sleepOffset)
                            }
                        }
                    }
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

    @Suppress("unused")
    private fun getNaluType(nalu: Byte): Int = ((nalu.toInt() and 0x07E) shr 1)
}
