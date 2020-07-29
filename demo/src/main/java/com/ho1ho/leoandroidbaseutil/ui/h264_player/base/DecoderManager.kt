package com.ho1ho.leoandroidbaseutil.ui.h264_player.base

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil

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

    @Volatile
    private var isDecodeFinish = false
    private val mSpeedController: SpeedManager = SpeedManager()
    private val mDecodeMp4Thread: DecoderMP4Thread = DecoderMP4Thread()
//    private val mDecodeH264Thread: DecoderH264Thread = DecoderH264Thread()

    /**
     * * Synchronized callback decoding
     */
    private fun init(videoFile: String, surface: Surface, width: Int, height: Int) {
        kotlin.runCatching {
            mediaCodec = MediaCodec.createDecoderByType("video/avc")
            mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height)
            mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(videoFile)
            LLog.d(TAG, "getTrackCount: " + mediaExtractor.trackCount)
            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                LLog.d(TAG, "mime: $mime")
                if (mime.startsWith("video")) {
                    mediaFormat = format
                    mediaExtractor.selectTrack(i)
                }
            }
            mediaCodec.configure(mediaFormat, surface, null, 0)
            mediaCodec.start()
        }.onFailure { ToastUtil.showErrorToast("Init Decoder error") }
    }

    /**
     * Play the MP4 file Thread
     */
    private class DecoderMP4Thread : Thread() {
        override fun run() {
            while (!isDecodeFinish) {
                kotlin.runCatching {
                    val inputIndex = mediaCodec.dequeueInputBuffer(-1)
                    LLog.d(TAG, " inputIndex: $inputIndex")
                    if (inputIndex >= 0) {
                        mediaCodec.getInputBuffer(inputIndex)?.let {
                            val sampleSize = mediaExtractor.readSampleData(it, 0)
                            val time = mediaExtractor.sampleTime
                            if (sampleSize > 0 && time > 0) {
                                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, time, 0)
                                mSpeedController.preRender(time)
                                mediaExtractor.advance()
                            }
                        }
                    }
                    val outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outIndex >= 0) {
                        mediaCodec.releaseOutputBuffer(outIndex, true)
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

    fun startMP4Decode(videoFile: String, surface: Surface, width: Int, height: Int) {
        init(videoFile, surface, width, height)
        mDecodeMp4Thread.name = "DecoderMP4Thread"
        mDecodeMp4Thread.start()
    }

//    fun startH264Decode() {
//        init()
//        mDecodeH264Thread.name = "DecoderH264Thread"
//        mDecodeH264Thread.start()
//    }
}