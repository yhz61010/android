package com.leovp.demo.basic_components.examples.media_player.base

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.view.Surface
import com.leovp.lib_bytes.toHexStringLE
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import java.io.File
import java.io.FileOutputStream

/**
 * Author: Michael Leo
 * Date: 20-7-28 下午4:53
 */
class DecoderVideoFileManager {
    private lateinit var mediaExtractor: MediaExtractor
    private var mediaCodec: MediaCodec? = null
    private var outputFormat: MediaFormat? = null
    private val mSpeedController: SpeedManager = SpeedManager()

    private val outputVideoRawDataFile: FileOutputStream by lazy { FileOutputStream(File(Environment.getExternalStorageDirectory(), "h265.h265")) }

    var videoWidth: Int = 0
    var videoHeight: Int = 0

    fun init(videoFile: String, surface: Surface) {
        runCatching {
            mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(videoFile)
            LogContext.log.d(TAG, "getTrackCount: " + mediaExtractor.trackCount)
            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val width = format.getInteger(MediaFormat.KEY_WIDTH)
                val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                val keyFrameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                val csd0 = format.getByteBuffer("csd-0")!!
                // We must make a copy of that csd0 value from format. Or else the video will not be played.
                val copiedCsd0 = csd0.duplicate()
//                val csd1 = format.getByteBuffer("csd-1")!!
                val csd0ByteArray = ByteArray(copiedCsd0.remaining())
//                val csd1ByteArray = ByteArray(csd1.remaining())
                copiedCsd0.get(csd0ByteArray)
//                csd1.get(csd1ByteArray)
                LogContext.log.w(TAG, "csd0=HEX[${csd0ByteArray.toHexStringLE()}]")
//                LogContext.log.d(TAG, "csd1=${csd0ByteArray.toHexString()}")
                outputVideoRawDataFile.write(csd0ByteArray)
//                videoRawDataFile.write(csd1ByteArray)
                videoWidth = width
                videoHeight = height
                LogContext.log.w(TAG, "mime=$mime width=$width height=$height keyFrameRate=$keyFrameRate")
                if (mime.startsWith("video")) {
                    mediaExtractor.selectTrack(i)
                    // MediaFormat.MIMETYPE_VIDEO_AVC  MediaFormat.MIMETYPE_VIDEO_HEVC
                    mediaCodec = MediaCodec.createDecoderByType(mime).apply {
                        configure(format, surface, null, 0)
                        setCallback(mediaCodecCallback)
                    }
                    break
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            runCatching {
                codec.getInputBuffer(index)?.let {
                    val sampleSize = mediaExtractor.readSampleData(it, 0)
                    if (sampleSize > 0) {
                        val outByteArray = ByteArray(sampleSize)
                        it.get(outByteArray)
                        outputVideoRawDataFile.write(outByteArray)
                    }
                    val time = mediaExtractor.sampleTime
                    LogContext.log.d(TAG, "sampleSize=$sampleSize\tsampleTime=$time")
                    if (sampleSize > 0 && time > 0) {
                        codec.queueInputBuffer(index, 0, sampleSize, time, 0)
                        mediaExtractor.advance()
                    } else {
                        LogContext.log.w(TAG, "Decode done")
                        outputVideoRawDataFile.flush()
                        outputVideoRawDataFile.close()
                    }
                }
            }.onFailure {
                LogContext.log.d(TAG, "decode mp4 error", it)
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            runCatching {
                LogContext.log.d(TAG, "bufferInfo.presentationTime=${info.presentationTimeUs}")
                mSpeedController.preRender(info.presentationTimeUs)
                codec.releaseOutputBuffer(index, true)
            }.onFailure { LogContext.log.d(TAG, "onOutputBufferAvailable error", it) }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LogContext.log.w(ITAG, "onOutputFormatChanged format=$format")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogContext.log.e(ITAG, "onError e=${e.message}")
        }
    }

    fun close() {
        runCatching {
            LogContext.log.d(TAG, "close start")
            mediaCodec?.stop()
            mediaCodec?.release()
            mSpeedController.reset()
        }.onFailure { LogContext.log.e(TAG, "close error") }
    }

    fun startDecoding() {
        mediaCodec?.start()
    }

    companion object {
        private const val TAG = "DecoderManager"
    }
}
