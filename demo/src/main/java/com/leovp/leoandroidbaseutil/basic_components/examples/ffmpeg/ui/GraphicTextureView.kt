@file:Suppress("MemberVisibilityCanBePrivate")

package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.ui

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.AttributeSet
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.ViewGroup
import androidx.annotation.Keep
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.ui.base.GraphicTouchHelper
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.media.VideoUtil
import com.leovp.lib_bytes.toHexStringLE
import com.leovp.log_sdk.LogContext
import org.greenrobot.eventbus.EventBus
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue


/**
 * Author: Michael Leo
 * Date: 2021/4/28 10:30 AM
 */
class GraphicTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), SurfaceTextureListener {
    companion object {
        private const val TAG = "GTV"
    }

    var touchHelper: GraphicTouchHelper? = null

    fun setTouchListener(listener: GraphicTouchHelper.TouchListener) {
        touchHelper = GraphicTouchHelper(listener)
    }

    //    private var outYUVOutputFile: BufferedOutputStream

    init {
        surfaceTextureListener = this
        //        outYUVOutputFile = BufferedOutputStream(FileOutputStream("/sdcard/remote.yuv"))
    }

    private var mySurfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    var graphicViewDestroyListener: GraphicViewDestroyListener? = null

    private var hevcDecoder: MediaCodec? = null
    var outputFormat: MediaFormat? = null
        private set

    private var frameCount: Long = 0
    val queue = ArrayBlockingQueue<ByteArray>(10)

    override fun onSurfaceTextureAvailable(
        pSurfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        LogContext.log.i(TAG, "onSurfaceTextureAvailable() width=$width height=$height")
        mySurfaceTexture?.let { setSurfaceTexture(it) }
        this.surface = Surface(pSurfaceTexture)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        //        if (GlobalConstants.OUTPUT_LOG) LogContext.log.w(TAG, "onSurfaceTextureUpdated()")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        LogContext.log.i(TAG, "onSurfaceTextureSizeChanged() width=$width height=$height")
        //        releaseAvcDecoder()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        LogContext.log.w(TAG, "onSurfaceTextureDestroyed()")
        //        runCatching {
        //            outYUVOutputFile.flush()
        //            outYUVOutputFile.closeQuietly()
        //        }.onFailure { it.printStackTrace() }
        graphicViewDestroyListener?.onDestroy()
        mySurfaceTexture = surface
        releaseDecoder()
        return false
    }

    override fun onDetachedFromWindow() {
        LogContext.log.w(TAG, "onDetachedFromWindow()")
        super.onDetachedFromWindow()
        runCatching {
            mySurfaceTexture?.release()
            surface?.release()
        }.getOrNull()
    }

    // ----------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHelper?.onTouchEvent(event) ?: performClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ----------------------------------------

    interface GraphicViewDestroyListener {
        fun onDecoderRelease()
        fun onDestroy()
    }

    // =========================================

    fun initDecoder(vps: ByteArray, sps: ByteArray, pps: ByteArray, screenInfo: Size) {
        runCatching {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, screenInfo.width, screenInfo.height)
            val csd0 = vps + sps + pps
            LogContext.log.w(TAG, "H265 csd0[${csd0.size}]=${csd0.toHexStringLE()}")
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            //            releaseAvcDecoder()
            //                avcDecoder = MediaCodec.createByCodecName("OMX.google.hevc.decoder")
            hevcDecoder = if (CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_HEVC, "c2.android.hevc.decoder", encoder = false)) {
                MediaCodec.createByCodecName("c2.android.hevc.decoder")
            } else {
                MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            }

            hevcDecoder?.let {
                it.configure(format, this.surface, null, 0)
                outputFormat = it.outputFormat // option B
                it.setCallback(mediaCodecCallback)
                it.start()
            }
        }.onFailure {
            LogContext.log.e(TAG, "initDecoder error. msg=${it.message}", it)
        }
    }

    // In most of Samsung devices, we MUST release MediaCodec before using it again.
    // Otherwise, in some cases, it will not display anything anymore.
    fun releaseDecoder() {
        if (hevcDecoder != null) {
            runCatching {
                LogContext.log.w(TAG, "Try to release avcDecoder")
                hevcDecoder?.release()
            }.onFailure {
                it.printStackTrace()
                LogContext.log.e(TAG, "Release avcDecoder error. Known issue. msg=${it.message}")
            }.also {
                hevcDecoder = null
                System.gc()
                graphicViewDestroyListener?.onDecoderRelease()
            }
        }
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            runCatching {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also { inputBuffer?.put(it) }
                //                data?.let { videoData ->
                //                    val naluTypeName = if (isH265) H265Util.getNaluTypeName(videoData) else H264Util.getNaluTypeName(videoData)
                //                    if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(TAG, "RVID[${videoData.size.toString().padStart(5)}\t$naluTypeName]")
                //                }
                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++frameCount), 0)
            }.onFailure {
                LogContext.log.v(TAG, "You can ignore this error. ${it.message}")
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            runCatching {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
                    //                 if (GlobalConstants.OUTPUT_LOG) LogContext.log.i(TAG, "onOutputBufferAvailable length=${info.size}")
                    when (info.flags) {
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG  -> {
                            val decodedData = ByteArray(info.size)
                            it.get(decodedData)
                            LogContext.log.w(TAG, "Found SPS/PPS frame: ${decodedData.contentToString()}")
                        }
                        MediaCodec.BUFFER_FLAG_KEY_FRAME     -> {
                            //                            if (GlobalConstants.OUTPUT_LOG)  if (GlobalConstants.OUTPUT_LOG) LogContext.log.d(TAG, "Found Key Frame[" + info.size + "]")
                        }
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM -> Unit
                        MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> Unit
                        else                                 -> Unit
                    }
                }
                codec.releaseOutputBuffer(outputBufferId, true)
            }.onFailure { it.printStackTrace() }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LogContext.log.w(TAG, "onOutputFormatChanged format=$format")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B

            runCatching {
                val width = format.getInteger("width")
                val height = format.getInteger("height")
                //                if (width > height) {
                //                    width -= 8
                //                } else {
                //                    height -= 8
                //                }
                EventBus.getDefault().post(VideoOutputFormatChangeEvent(Point(width, height)))
            }.onFailure {
                LogContext.log.e(TAG, "Get video dimension error.", it)
                context.toast("Get video dimension error.", debug = true, error = true)
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogContext.log.e(TAG, "onError e=${e.message}", e)
        }
    }

    fun updateDimension(width: Int, height: Int) {
        LogContext.log.w(TAG, "Adjust TextureView dimension to $width x $height")
        val params: ViewGroup.LayoutParams = layoutParams
        // Changes the height and width to the specified *pixels*
        // Changes the height and width to the specified *pixels*
        params.width = width
        params.height = height
        layoutParams = params
    }

    fun updateBitrate(bitrate: Int) {
        LogContext.log.w(TAG, "Change bitrate to $bitrate")
        hevcDecoder?.let { VideoUtil.setBitrateDynamically(it, bitrate) }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 120
}

@Keep
class VideoOutputFormatChangeEvent(val videoDimen: Point)