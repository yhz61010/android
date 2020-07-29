package com.ho1ho.leoandroidbaseutil.ui.media_player.base

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.view.Surface
import com.ho1ho.androidbase.exts.toHexString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import java.io.File
import java.io.FileOutputStream

/**
 * Author: Michael Leo
 * Date: 20-7-28 下午4:53
 */
object DecoderManager {
    private const val TAG = "DecoderManager"

    private lateinit var mediaExtractor: MediaExtractor
    private lateinit var mediaCodec: MediaCodec
    private val bufferInfo = MediaCodec.BufferInfo()
    private var mediaFormat: MediaFormat? = null

    private val videoRawDataFile: FileOutputStream by lazy { FileOutputStream(File(Environment.getExternalStorageDirectory(), "h265.h265")) }

    @Volatile
    private var isDecodeFinish = false
    private val mSpeedController: SpeedManager = SpeedManager()
    private val mDecodeMp4Thread: DecoderMP4Thread = DecoderMP4Thread()

    var videoWidth: Int = 0
    var videoHeight: Int = 0

//    private val mDecodeH264Thread: DecoderH264Thread = DecoderH264Thread()

    /**
     * * Synchronized callback decoding
     */
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
                videoRawDataFile.write(csd0ByteArray)
//                videoRawDataFile.write(csd1ByteArray)
                videoWidth = width
                videoHeight = height
                LLog.w(TAG, "mime=$mime width=$width height=$height keyFrameRate=$keyFrameRate")
                if (mime.startsWith("video")) {
                    mediaFormat = format
                    mediaExtractor.selectTrack(i)

//                    mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC) // MediaFormat.MIMETYPE_VIDEO_AVC  MediaFormat.MIMETYPE_VIDEO_HEVC
                    mediaCodec = MediaCodec.createDecoderByType(mime)
                    mediaCodec.configure(mediaFormat, surface, null, 0)
                    mediaCodec.start()
                    break
                }
            }

        }.onFailure { it.printStackTrace();ToastUtil.showErrorToast("Init Decoder error") }
    }

    /**
     * Play the MP4 file Thread
     */
    private class DecoderMP4Thread : Thread() {
        override fun run() {
            while (!isDecodeFinish) {
                kotlin.runCatching {
                    val inputIndex = mediaCodec.dequeueInputBuffer(0)
                    if (inputIndex >= 0) {
                        mediaCodec.getInputBuffer(inputIndex)?.let {
                            val sampleSize = mediaExtractor.readSampleData(it, 0)
                            if (sampleSize > 0) {
                                val outByteArray = ByteArray(sampleSize)
                                it.get(outByteArray)
                                videoRawDataFile.write(outByteArray)
                            }
                            val time = mediaExtractor.sampleTime
                            LLog.d(TAG, "sampleSize=$sampleSize\tsampleTime=$time")
                            if (sampleSize > 0 && time > 0) {
                                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, time, 0)
                                mediaExtractor.advance()
                            } else {
                                LLog.w(TAG, "Decode done")
                                isDecodeFinish = true
                                videoRawDataFile.flush()
                                videoRawDataFile.close()
                            }
                        }
                    }
                    var outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
//                    LLog.w(TAG, "outIndex=$outIndex")
                    while (outIndex >= 0) {
                        LLog.d(TAG, "bufferInfo.presentationTime=${bufferInfo.presentationTimeUs}")
                        mSpeedController.preRender(bufferInfo.presentationTimeUs)
                        mediaCodec.releaseOutputBuffer(outIndex, true)
                        outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }.onFailure {
                    LLog.e(TAG, "decode mp4 error")
                }
            }
        }
    }

//    private class DecoderH264Thread : Thread() {
//        var pts: Long = 0
//        override fun run() {
//            super.run()
//            val startTime = System.nanoTime()
//            while (!isDecodeFinish) {
//                val inputIndex = mediaCodec.dequeueInputBuffer(-1)
//                if (inputIndex >= 0) {
//                    mediaCodec.getInputBuffer(inputIndex)?.let {
//                        val sampleSize: Int = DecodeH264File.getInstance().readSampleData(it)
//                        val time = (System.nanoTime() - startTime) / 1000
//                        if (sampleSize > 0 && time > 0) {
//                            mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, time, 0)
//                            mSpeedController.preRender(time)
//                        }
//                    }
//                }
//                val outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
//                if (outIndex >= 0) {
//                    mediaCodec.releaseOutputBuffer(outIndex, true)
//                }
//            }
//        }
//    }

    fun close() {
        kotlin.runCatching {
            LLog.d(TAG, "close start")
            isDecodeFinish = true
            mDecodeMp4Thread.join(2000)
//            mDecodeH264Thread.join(2000)
            val isAlive = mDecodeMp4Thread.isAlive
            LLog.d(TAG, "close end isAlive :$isAlive")
            mediaCodec.stop()
            mediaCodec.release()
            mSpeedController.reset()
        }.onFailure { LLog.e(TAG, "close error") }

        // FIXME If deocde H264 raw stream, do not forget to close it when you don't need it
//        DecodeH264File.close()
    }

    fun startMP4Decode() {
        mDecodeMp4Thread.name = "DecoderMP4Thread"
        mDecodeMp4Thread.start()
    }

//    fun startH264Decode() {
//        init()
//        mDecodeH264Thread.name = "DecoderH264Thread"
//        mDecodeH264Thread.start()
//    }
}