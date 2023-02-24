package com.leovp.demo.basiccomponents.examples.ffmpeg.utils

import android.os.SystemClock
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.androidbase.exts.kotlin.truncate
import com.leovp.bytes.toHexStringLE
import com.leovp.ffmpeg.video.H264HevcDecoder
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.opengl.BaseRenderer
import com.leovp.opengl.ui.LeoGLSurfaceView
import java.io.File
import java.io.RandomAccessFile
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
    private lateinit var glSurfaceView: LeoGLSurfaceView

    private lateinit var videoInfo: H264HevcDecoder.DecodeVideoInfo
    private var csd0Size: Int = 0

    private val videoDecoder = H264HevcDecoder()

    fun init(videoFile: String, glSurfaceView: LeoGLSurfaceView) {
        this.glSurfaceView = glSurfaceView
        rf = RandomAccessFile(File(videoFile), "r")
        LogContext.log.w(TAG, "File length=${rf.length()}")

        val sps = getNalu()!!
        val pps = getNalu()!!

        LogContext.log.w(TAG, "sps[${sps.size}]=${sps.toHexStringLE()}")
        LogContext.log.w(TAG, "pps[${pps.size}]=${pps.toHexStringLE()}")

        val csd0 = sps + pps
        LogContext.log.w(TAG, "csd0[${csd0.size}]=${csd0.toHexStringLE().truncate(180)}")
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
            videoDecoder.init(null, sps, pps, null, null)
        LogContext.log.w(TAG, "Decoded videoInfo=${videoInfo.toJsonString()}")
        return videoInfo
    }

    private fun decodeVideo(rawVideo: ByteArray): H264HevcDecoder.DecodedVideoFrame? =
        videoDecoder.decode(rawVideo)

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
                    val bytes = getRawH264() ?: break
                    var previousStart = 0
                    for (i in startIdx until bytes.size) {
                        ensureActive()
                        if (findStartCode4(bytes, i)) {
                            val frame = ByteArray(i - previousStart)
                            System.arraycopy(bytes, previousStart, frame, 0, frame.size)

                            val st1 = SystemClock.elapsedRealtime()
                            var st3: Long
                            try {
                                val decodeFrame: H264HevcDecoder.DecodedVideoFrame? =
                                    decodeVideo(frame)
                                val st2 = SystemClock.elapsedRealtimeNanos()
                                decodeFrame?.let {
                                    val yuv420Type = if (videoInfo.pixelFormatId < 0) {
                                        BaseRenderer.Yuv420Type.I420
                                    } else {
                                        BaseRenderer.Yuv420Type.getType(videoInfo.pixelFormatId)
                                    }
                                    glSurfaceView.render(it.yuvBytes, yuv420Type)
                                }
                                st3 = SystemClock.elapsedRealtimeNanos()
                                LogContext.log.w(
                                    TAG,
                                    "frame[${frame.size}][decode cost=${st2 / 1000_000 - st1}ms][render cost=${(st3 - st2) / 1000}us] " +
                                        "${decodeFrame?.width}x${decodeFrame?.height}"
                                )
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
