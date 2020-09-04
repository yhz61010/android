package com.ho1ho.leoandroidbaseutil.basic_components.examples.media_player.base

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.view.Surface
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toHexString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import java.io.File
import java.io.FileOutputStream

/**
 * Author: Michael Leo
 * Date: 20-7-28 下午4:53
 */
class DecoderVideoFileManager {
    private lateinit var mediaExtractor: MediaExtractor
    private lateinit var mediaCodec: MediaCodec
    private var outputFormat: MediaFormat? = null
    private val mSpeedController: SpeedManager = SpeedManager()

    private val outputVideoRawDataFile: FileOutputStream by lazy { FileOutputStream(File(Environment.getExternalStorageDirectory(), "h265.h265")) }

    var videoWidth: Int = 0
    var videoHeight: Int = 0

    fun init(videoFile: String, surface: Surface) {
        kotlin.runCatching {
            mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(videoFile)
            LLog.d(TAG, "getTrackCount: " + mediaExtractor.trackCount)
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
                LLog.w(TAG, "csd0=${csd0ByteArray.toHexString()}")
//                LLog.d(TAG, "csd1=${csd0ByteArray.toHexString()}")
                outputVideoRawDataFile.write(csd0ByteArray)
//                videoRawDataFile.write(csd1ByteArray)
                videoWidth = width
                videoHeight = height
                LLog.w(TAG, "mime=$mime width=$width height=$height keyFrameRate=$keyFrameRate")
                if (mime.startsWith("video")) {
                    mediaExtractor.selectTrack(i)
                    mediaCodec = MediaCodec.createDecoderByType(mime) // MediaFormat.MIMETYPE_VIDEO_AVC  MediaFormat.MIMETYPE_VIDEO_HEVC
                    mediaCodec.configure(format, surface, null, 0)
                    mediaCodec.setCallback(mediaCodecCallback)
                    break
                }
            }

        }.onFailure { it.printStackTrace();ToastUtil.showErrorToast("Init Decoder error") }
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            kotlin.runCatching {
                codec.getInputBuffer(index)?.let {
                    val sampleSize = mediaExtractor.readSampleData(it, 0)
                    if (sampleSize > 0) {
                        val outByteArray = ByteArray(sampleSize)
                        it.get(outByteArray)
                        outputVideoRawDataFile.write(outByteArray)
                    }
                    val time = mediaExtractor.sampleTime
                    LLog.d(TAG, "sampleSize=$sampleSize\tsampleTime=$time")
                    if (sampleSize > 0 && time > 0) {
                        codec.queueInputBuffer(index, 0, sampleSize, time, 0)
                        mediaExtractor.advance()
                    } else {
                        LLog.w(TAG, "Decode done")
                        outputVideoRawDataFile.flush()
                        outputVideoRawDataFile.close()
                    }
                }
            }.onFailure {
                LLog.e(TAG, "decode mp4 error", it)
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            kotlin.runCatching {
                LLog.d(TAG, "bufferInfo.presentationTime=${info.presentationTimeUs}")
                mSpeedController.preRender(info.presentationTimeUs)
                codec.releaseOutputBuffer(index, true)
            }.onFailure { LLog.e(TAG, "onOutputBufferAvailable error", it) }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LLog.w(ITAG, "onOutputFormatChanged format=$format")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LLog.e(ITAG, "onError e=${e.message}")
        }
    }

    fun close() {
        kotlin.runCatching {
            LLog.d(TAG, "close start")
            mediaCodec.stop()
            mediaCodec.release()
            mSpeedController.reset()
        }.onFailure { LLog.e(TAG, "close error") }
    }

    fun startDecoding() {
        mediaCodec.start()
    }

    companion object {
        private const val TAG = "DecoderManager"
    }
}