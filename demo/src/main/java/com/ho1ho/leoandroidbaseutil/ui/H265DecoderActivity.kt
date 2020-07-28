package com.ho1ho.leoandroidbaseutil.ui

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.SurfaceHolder
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_h265_decoder.*
import java.io.File
import java.nio.ByteBuffer


class H265DecoderActivity : BaseDemonstrationActivity() {

    private val mediaExtractor = MediaExtractor()
    private lateinit var mediaFormat: MediaFormat
    private lateinit var mediaCodec: MediaCodec
    private var isDecodeFinish: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_h265_decoder)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                val videoFile = File(Environment.getExternalStorageDirectory(), "h264.h264")
                LLog.d(ITAG, "video file: $videoFile")

                mediaExtractor.setDataSource(videoFile.absolutePath)
                LLog.d(ITAG, "getTrackCount: " + mediaExtractor.trackCount)
                for (i in 0 until mediaExtractor.trackCount) {
                    val format: MediaFormat = mediaExtractor.getTrackFormat(i)
                    val mime: String = format.getString(MediaFormat.KEY_MIME)!!
                    //If it's video format
                    if (mime.startsWith("video")) {
                        mediaFormat = format
                        mediaExtractor.selectTrack(i)
                    }
                }

                mediaCodec = MediaCodec.createDecoderByType("video/avc")
                mediaFormat = MediaFormat.createVideoFormat("video/avc", 640, 360)
                val surface: Surface = surfaceView.holder.surface
                //A surface must be configured as its output decoding display screen
                //A surface must be configured as its output decoding display screen
                mediaCodec.configure(mediaFormat, surface, null, 0)
                mediaCodec.start()

                val t = DecoderMP4Thread()
                t.start()
            }
        })
    }

    inner class DecoderMP4Thread : Thread() {
        var pts: Long = 0
        override fun run() {
            super.run()
            while (!isDecodeFinish) {
                val inputIndex: Int = mediaCodec.dequeueInputBuffer(-1)
                LLog.d(ITAG, "inputIndex: $inputIndex")
                if (inputIndex >= 0) {
                    val byteBuffer: ByteBuffer = mediaCodec.getInputBuffer(inputIndex)!!
                    //Read a piece or frame of data
                    val sampSize: Int = mediaExtractor.readSampleData(byteBuffer, 0)
                    //Read timestamp
                    val time: Long = mediaExtractor.sampleTime
                    LLog.d(ITAG, "sampleSize: $sampSize\ttime: $time")
                    if (sampSize > 0 && time > 0) {
                        mediaCodec.queueInputBuffer(inputIndex, 0, sampSize, time, 0)
                        //After reading one frame, it must be called to extract the next frame
                        mediaExtractor.advance()
                        //Control the frame rate at about 30 frames
//                        try {
//                            sleep(30)
//                        } catch (e: InterruptedException) {
//                            e.printStackTrace()
//                        }
                    }
                }
                val bufferInfo = BufferInfo()
                val outIndex: Int = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                LLog.d(ITAG, "outIndex: $outIndex")
                if (outIndex >= 0) {
                    mediaCodec.releaseOutputBuffer(outIndex, true)
                }
            }
        }
    }

    fun close() {
        if (mediaCodec != null) {
            mediaCodec.stop()
            mediaCodec.release()
            isDecodeFinish = true
        }
    }
}