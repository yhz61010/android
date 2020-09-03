package com.ho1ho.leoandroidbaseutil.common_components.examples.media_player.base

import android.content.Context
import android.media.*
import android.os.Environment
import com.ho1ho.androidbase.utils.LLog
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-7-28 下午4:54
 */
object AudioPlayManager {
    private val TAG = AudioTrack::class.java.simpleName
    private val videoFile = File(Environment.getExternalStorageDirectory(), "h265.mp4")

    private val mSampleRate = 44100
    private val channelCount = 2
    private var mAudioTrack: AudioTrack? = null
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private var bufferSize = 0
    private val audioFormatEncode = AudioFormat.ENCODING_PCM_16BIT
    private var mAudioManager: AudioManager? = null
    private var mContext: Context? = null
    private val mAudioFormat: AudioFormat? = null
    private val fileInputStream: FileInputStream? = null
    private val startPlay = false
    private var mediaCodec: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null
    private var mediaExtractor: MediaExtractor? = null
    private var isDecodeFinish = false
    fun setContext(context: Context?) {
        mContext = context
        init()
        initMediaExactor()
    }

    private fun init() {
        mAudioManager = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bufferSize = AudioTrack.getMinBufferSize(mSampleRate, channelConfig, audioFormatEncode)
        val sessionId = mAudioManager!!.generateAudioSessionId()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val audioFormat = AudioFormat.Builder().setSampleRate(mSampleRate)
            .setEncoding(audioFormatEncode)
            .setChannelMask(channelConfig)
            .build()
        mAudioTrack = AudioTrack(audioAttributes, audioFormat, bufferSize * 2, AudioTrack.MODE_STREAM, sessionId)
    }

    private fun initMediaExactor() {
        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, mSampleRate, channelCount)
            mediaExtractor = MediaExtractor()
            mediaExtractor!!.setDataSource(videoFile.absolutePath)
            LLog.d(TAG, "getTrackCount: " + mediaExtractor!!.trackCount)
            for (i in 0 until mediaExtractor!!.trackCount) {
                val format = mediaExtractor!!.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                LLog.d(TAG, "mime: $mime")
                if (mime.startsWith("audio")) {
                    mediaFormat = format
                    mediaExtractor!!.selectTrack(i)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mediaCodec!!.configure(mediaFormat, null, null, 0)
        mediaCodec!!.start()
    }

    fun startThread() {
        isDecodeFinish = false
        PlayThread().start()
    }

    fun close() {
        isDecodeFinish = true
        mAudioTrack!!.stop()
        mAudioTrack!!.release()
//        instance = null
    }

    internal class PlayThread : Thread() {
        override fun run() {
            super.run()
            mAudioTrack!!.play()
            while (!isDecodeFinish) {
                val inputIndex = mediaCodec!!.dequeueInputBuffer(-1)
                LLog.d(TAG, "inputIndex: $inputIndex")
                if (inputIndex >= 0) {
                    val byteBuffer = mediaCodec!!.getInputBuffer(inputIndex)
                    //读取一片或者一帧数据
                    val sampSize = mediaExtractor!!.readSampleData(byteBuffer!!, 0)
                    //读取时间戳
                    val time = mediaExtractor!!.sampleTime
                    if (sampSize > 0 && time >= 0) {
                        mediaCodec!!.queueInputBuffer(inputIndex, 0, sampSize, time, 0)
                        //读取一帧后必须调用，提取下一帧
                        mediaExtractor!!.advance()
                        //控制帧率在30帧左右
                    }
                }
                val bufferInfo = MediaCodec.BufferInfo()
                val outIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 0)
                if (outIndex >= 0) {
                    val byteBuffer = mediaCodec!!.getOutputBuffer(outIndex)
                    val bytes = ByteArray(bufferInfo.size)
                    byteBuffer!![bytes]
                    mAudioTrack!!.write(bytes, 0, bytes.size)
                    mediaCodec!!.releaseOutputBuffer(outIndex, true)
                }
            }
        }
    }

}