package com.ho1ho.leoandroidbaseutil.ui.h264_player.base

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.ho1ho.leoandroidbaseutil.ui.h264_player.PlayMp4ByMediaCodecH264Activity
import com.ho1ho.leoandroidbaseutil.ui.h264_player.mp4File
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-7-28 下午4:53
 */
class DecoderManager private constructor() {
    private var mediaCodec: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null
    private val frameIndex: Long = 0

    @Volatile
    private var isDecodeFinish = false
    private var mediaExtractor: MediaExtractor? = null
    private val mSpeedController: SpeedManager =
        SpeedManager()
    private var mDecodeMp4Thread: DecoderMP4Thread? = null
    private var mDecodeH264Thread: DecoderH264Thread? = null

    /**
     * * Synchronized callback decoding
     */
    private fun initMediaCodecSys() {
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc")
            mediaFormat = MediaFormat.createVideoFormat("video/avc", 1080, 1920)
            mediaExtractor = MediaExtractor()
            mediaExtractor!!.setDataSource(mp4File.absolutePath)
            Log.d(TAG, "getTrackCount: " + mediaExtractor!!.trackCount)
            for (i in 0 until mediaExtractor!!.trackCount) {
                val format = mediaExtractor!!.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                Log.d(TAG, "mime: $mime")
                if (mime.startsWith("video")) {
                    mediaFormat = format
                    mediaExtractor!!.selectTrack(i)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mediaCodec!!.configure(mediaFormat, PlayMp4ByMediaCodecH264Activity.surface, null, 0)
        mediaCodec!!.start()
    }

    /**
     * Play the MP4 file Thread
     */
    private inner class DecoderMP4Thread : Thread() {
        var pts: Long = 0
        override fun run() {
            super.run()
            while (!isDecodeFinish) {
                val inputIndex = mediaCodec!!.dequeueInputBuffer(-1)
                Log.d(TAG, " inputIndex: $inputIndex")
                if (inputIndex >= 0) {
                    val byteBuffer = mediaCodec!!.getInputBuffer(inputIndex)
                    //读取一片或者一帧数据
                    val sampSize = mediaExtractor!!.readSampleData(byteBuffer!!, 0)
                    //读取时间戳
                    val time = mediaExtractor!!.sampleTime
                    if (sampSize > 0 && time > 0) {
                        mediaCodec!!.queueInputBuffer(inputIndex, 0, sampSize, time, 0)
                        //读取一帧后必须调用，提取下一帧
                        //控制帧率在30帧左右
                        mSpeedController.preRender(time)
                        mediaExtractor!!.advance()
                    }
                }
                val bufferInfo = MediaCodec.BufferInfo()
                val outIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 0)
                if (outIndex >= 0) {
                    mediaCodec!!.releaseOutputBuffer(outIndex, true)
                }
            }
        }
    }

    private inner class DecoderH264Thread : Thread() {
        var pts: Long = 0
        override fun run() {
            super.run()
            val startTime = System.nanoTime()
            while (!isDecodeFinish) {
                if (mediaCodec != null) {
                    val inputIndex = mediaCodec!!.dequeueInputBuffer(-1)
                    if (inputIndex >= 0) {
                        val byteBuffer = mediaCodec!!.getInputBuffer(inputIndex)!!
                        val sampSize: Int = DecodeH264File.getInstance().readSampleData(byteBuffer)
                        val time = (System.nanoTime() - startTime) / 1000
                        if (sampSize > 0 && time > 0) {
                            mediaCodec!!.queueInputBuffer(inputIndex, 0, sampSize, time, 0)
                            mSpeedController.preRender(time)
                        }
                    }
                }
                val bufferInfo = MediaCodec.BufferInfo()
                val outIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 0)
                if (outIndex >= 0) {
                    mediaCodec!!.releaseOutputBuffer(outIndex, true)
                }
            }
        }
    }

    fun close() {
        try {
            Log.d(TAG, "close start")
            if (mediaCodec != null) {
                isDecodeFinish = true
                try {
                    if (mDecodeMp4Thread != null) {
                        mDecodeMp4Thread!!.join(2000)
                    }
                    if (mDecodeH264Thread != null) {
                        mDecodeH264Thread!!.join(2000)
                    }
                } catch (e: InterruptedException) {
                    Log.e(TAG, "InterruptedException $e")
                }
                val isAlive = mDecodeMp4Thread!!.isAlive
                Log.d(TAG, "close end isAlive :$isAlive")
                mediaCodec!!.stop()
                mediaCodec!!.release()
                mediaCodec = null
                mSpeedController.reset()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        DecodeH264File.getInstance().close()
//        instance = null
    }

    fun startMP4Decode() {
        initMediaCodecSys()
        mDecodeMp4Thread = DecoderMP4Thread()
        mDecodeMp4Thread!!.name = "DecoderMP4Thread"
        mDecodeMp4Thread!!.start()
    }

    fun startH264Decode() {
        initMediaCodecSys()
        mDecodeH264Thread = DecoderH264Thread()
        mDecodeH264Thread!!.name = "DecoderH264Thread"
        mDecodeH264Thread!!.start()
    }

    companion object {
        private val TAG = DecoderManager::class.java.simpleName

        //    public static String PATH = "http://live.hkstv.hk.lxdns.com/live/hks/playlist.m3u8";
        private var instance: DecoderManager? = null
        fun getInstance(): DecoderManager {
            if (instance == null) {
                instance =
                    DecoderManager()
            }
            return instance!!
        }
    }
}