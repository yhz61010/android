package com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.leovp.androidbase.exts.toBytesLE
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.audio.player.PcmPlayer
import com.leovp.audio.recorder.MicRecorder
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.AudioActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base.AudioSenderWebSocket
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base.AudioSenderWebSocketHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.ConstantRetry
import kotlinx.coroutines.*
import java.net.URI


/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioSender {
    companion object {
        private const val TAG = "AudioSender"
    }

    private var ctx: Context? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private lateinit var senderClient: AudioSenderWebSocket
    private lateinit var senderHandler: AudioSenderWebSocketHandler

    private var micRecorder: MicRecorder? = null
    private var pcmPlayer: PcmPlayer? = null

    @Volatile
    private var startPlaying = false

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.i(TAG, "onConnected")
            ToastUtil.showDebugToast("onConnected")
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
//            LogContext.log.i(TAG, "onReceivedData: ${data?.toJsonString()}")
            when (data) {
                is String -> {
                    LogContext.log.i(TAG, "Loopback time=${SystemClock.elapsedRealtime() - data.toLong()} ms")
                }
                is ByteArray -> {
                    if (!startPlaying) {
                        startPlaying = true
                        pcmPlayer = PcmPlayer(ctx!!, AudioActivity.audioPlayCodec, 5)
                    }
                    LogContext.log.i(TAG, "onReceivedData Compressed PCM[${data.size}]")
                    ioScope.launch {
                        runCatching {
                            ensureActive()

//                            val readBuffer = ByteArray(2560)
//                            val arrayInputStream = ByteArrayInputStream(data)
//                            val inputStream = InflaterInputStream(arrayInputStream)
//                            val read = inputStream.read(readBuffer)
//                            val originalPcmData = readBuffer.copyOf(read)

                            pcmPlayer?.play(data)
                        }.onFailure { it.printStackTrace() }
                    }
                }
            }
        }

        override fun onDisconnected(netty: BaseNettyClient) {
            LogContext.log.w(TAG, "onDisconnect")
            ToastUtil.showDebugToast("onDisconnect")
            stopRecordingAndPlaying()
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            stopRecordingAndPlaying()
        }
    }

    private fun stopRecordingAndPlaying() {
        startPlaying = false
        pcmPlayer?.release()

        micRecorder?.stopRecord()
    }

    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        senderClient = AudioSenderWebSocket(uri, connectionListener, ConstantRetry(10, 2000))
        senderHandler = AudioSenderWebSocketHandler(senderClient)
        senderClient.initHandler(senderHandler)
        senderClient.connect()

        micRecorder = MicRecorder(AudioActivity.audioEncoderCodec, object : MicRecorder.RecordCallback {
            override fun onRecording(pcmData: ByteArray, st: Long, ed: Long) {
                ioScope.launch {
                    runCatching {
                        ensureActive()
//                    LogContext.log.i(ITAG, "PCM[${pcmData.size}] to be sent.")
                        val tsArray = st.toBytesLE()

//                        val targetOs = ByteArrayOutputStream(pcmData.size)
//                        DeflaterOutputStream(targetOs).use {
//                            it.write(pcmData)
//                            it.flush()
//                            it.finish()
//                        }
//                        val compressedData = targetOs.toByteArray()

                        val finalArray = ByteArray(tsArray.size + pcmData.size)
                        System.arraycopy(tsArray, 0, finalArray, 0, tsArray.size)
                        System.arraycopy(pcmData, 0, finalArray, tsArray.size, pcmData.size)
                        senderHandler.sendAudioToServer(finalArray)
                    }.onFailure { it.printStackTrace() }
                }
            }

            override fun onStop(stopResult: Boolean) {
            }

        }).apply { startRecord() }
    }

    fun stop() {
        stopRecordingAndPlaying()
        if (::senderClient.isInitialized) {
            senderClient.disconnectManually()
            senderClient.release()
        }
        ioScope.cancel()
    }
}