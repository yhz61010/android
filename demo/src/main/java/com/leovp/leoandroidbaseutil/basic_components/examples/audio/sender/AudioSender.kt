package com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender

import android.annotation.SuppressLint
import android.content.Context
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

    private var senderClient: AudioSenderWebSocket? = null
    private var senderHandler: AudioSenderWebSocketHandler? = null

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
            val pcmData = data as ByteArray
            if (!startPlaying) {
                startPlaying = true
                pcmPlayer = PcmPlayer(ctx!!, AudioActivity.audioPlayCodec, 5)
            }
            LogContext.log.i(TAG, "onReceivedData PCM[${pcmData.size}]")
            ioScope.launch {
                runCatching {
                    ensureActive()

//                    val readBuffer = ByteArray(2560)
//                    val arrayInputStream = ByteArrayInputStream(pcmData)
//                    val inputStream = InflaterInputStream(arrayInputStream)
//                    val read = inputStream.read(readBuffer)
//                    val originalPcmData = readBuffer.copyOf(read)

                    pcmPlayer?.play(pcmData)
                }.onFailure { it.printStackTrace() }
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
        senderClient = AudioSenderWebSocket(uri, connectionListener, ConstantRetry(10, 2000)).also {
            senderHandler = AudioSenderWebSocketHandler(it)
            it.initHandler(senderHandler)
            it.connect()
        }

        micRecorder = MicRecorder(AudioActivity.audioEncoderCodec, object : MicRecorder.RecordCallback {
            override fun onRecording(pcmData: ByteArray, st: Long, ed: Long) {
                runCatching {
//                    LogContext.log.i(ITAG, "PCM[${pcmData.size}] to be sent.")
//                    val targetOs = ByteArrayOutputStream(pcmData.size)
//                    DeflaterOutputStream(targetOs).use {
//                        it.write(pcmData)
//                        it.flush()
//                        it.finish()
//                    }
//                    val compressedData = targetOs.toByteArray()
                    senderHandler?.sendAudioToServer(pcmData)
                }.onFailure { it.printStackTrace() }
            }

            override fun onStop(stopResult: Boolean) {
            }
        }).apply { startRecord() }
    }

    fun stop() {
        stopRecordingAndPlaying()
        senderClient?.disconnectManually()
        senderClient?.release()
        ioScope.cancel()
    }
}