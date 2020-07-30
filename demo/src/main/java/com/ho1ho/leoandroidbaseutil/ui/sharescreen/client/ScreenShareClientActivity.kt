package com.ho1ho.leoandroidbaseutil.ui.sharescreen.client

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.androidbase.utils.media.H264Util
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_screen_share_client.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ScreenShareClientActivity : BaseDemonstrationActivity() {

    private var decoder: MediaCodec? = null
    private var outputFormat: MediaFormat? = null

    private var frameCount: Long = 0
    private val queue = ConcurrentLinkedQueue<ByteArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_share_client)

        val screenInfo = DeviceUtil.getResolution(this)
        surfaceView.holder.setFixedSize(screenInfo.x, screenInfo.y)
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                connectToServer()
            } else {
                disconnect()
            }
        }
    }

    private fun initDecoder(sps: ByteArray, pps: ByteArray) {
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder")
        val screenInfo = DeviceUtil.getResolution(this)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenInfo.x, screenInfo.y)
//        val sps = byteArrayOf(0, 0, 0, 1, 103, 66, -64, 51, -115, 104, 8, -127, -25, -66, 1, -31, 16, -115, 64)
//        val pps = byteArrayOf(0, 0, 0, 1, 104, -50, 1, -88, 53, -56)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        decoder?.configure(format, surfaceView.holder.surface, null, 0)
        outputFormat = decoder?.outputFormat // option B
        decoder?.setCallback(mediaCodecCallback)
        decoder?.start()
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            try {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also {
//                CLog.i(ITAG, "onInputBufferAvailable length=${it.size}")
                    inputBuffer?.put(it)
                }
                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++frameCount), 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            val outputBuffer = codec.getOutputBuffer(outputBufferId)
            // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
            // bufferFormat is equivalent to member variable outputFormat
            // outputBuffer is ready to be processed or rendered.
            outputBuffer?.let {
//                CLog.i(ITAG, "onOutputBufferAvailable length=${info.size}")
                when (info.flags) {
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                        val decodedData = ByteArray(info.size)
                        it.get(decodedData)
                        LLog.w(ITAG, "Found SPS/PPS frame: ${decodedData.contentToString()}")
                    }
                    MediaCodec.BUFFER_FLAG_KEY_FRAME -> LLog.i(ITAG, "Found Key Frame[" + info.size + "]")
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM -> {
                        // Do nothing
                    }
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> {
                        // Do nothing
                    }
                    else -> {
                        // Do nothing
                    }
                }
            }
            codec.releaseOutputBuffer(outputBufferId, true)
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

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 20

    // ============================================
    private var webSocketClient: WebSocketClient? = null

    inner class ScreenShareWebSocketClient(serverURI: URI) : WebSocketClient(serverURI) {
        private var firstData = AtomicBoolean(false)

        override fun onOpen(serverHandshake: ServerHandshake) {
            LLog.d(ITAG, "onOpen")
        }

        override fun onMessage(s: String) {
            LLog.d(ITAG, "onMessage: $s")
        }

        override fun onMessage(bytes: ByteBuffer) {
            val data = ByteArray(bytes.remaining())
            bytes.get(data)
            LLog.i(ITAG, "onMessage length=${data.size}")

            if (!firstData.get()) {
                LLog.w(ITAG, "first data=${data.contentToString()}")
                firstData.set(true)
                val sps = H264Util.getSps(data)
                val pps = H264Util.getPps(data)
                LLog.w(ITAG, "initDecoder with sps=${sps?.contentToString()} pps=${pps?.contentToString()}")
                if (sps != null && pps != null) {
                    initDecoder(sps, pps)
                    return
                } else {
                    ToastUtil.showErrorLongToast("Get SPS/PPS error.")
                    return
                }
            }

            queue.offer(data)
        }

        override fun onClose(i: Int, s: String, b: Boolean) {
            LLog.d(ITAG, "onClose")
            runOnUiThread { toggleButton.isChecked = false }
        }

        override fun onError(e: Exception) {
            LLog.e(ITAG, "onError")
            e.printStackTrace()
        }
    }

    private fun connectToServer() {
        // This ip is remote phone ip.
        // You can get this value as following:
        // 1. adb shell (Login your server phone)
        // 2. Execute: ip a
        // Find ip like: 10.10.9.126
        val url = URI("ws://${etServerIp.text}:10086")
        webSocketClient = ScreenShareWebSocketClient(url)
        webSocketClient?.connectBlocking()
    }

    private fun disconnect() {
        webSocketClient?.closeBlocking()
        webSocketClient?.close()
    }
}