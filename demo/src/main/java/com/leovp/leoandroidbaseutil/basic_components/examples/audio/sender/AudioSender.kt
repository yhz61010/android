package com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender

import android.annotation.SuppressLint
import android.content.Context
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.leoandroidbaseutil.BuildConfig
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.AudioActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.AudioReceiver.Companion.defaultAudioType
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base.AudioSenderWebSocket
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base.AudioSenderWebSocketHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.ConstantRetry
import kotlinx.coroutines.*
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue


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
    private var audioPlayer: AudioPlayer? = null

    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(10)
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(10)

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.i(TAG, "onConnected")
            ToastUtil.showDebugToast("onConnected")
            audioPlayer = AudioPlayer(ctx!!, AudioActivity.audioPlayCodec, defaultAudioType)
            sendRecAudioThread()
            startPlayThread()
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData Length[${audioData.size}]")
            receiveAudioQueue.offer(audioData)
        }

        override fun onDisconnected(netty: BaseNettyClient) {
            LogContext.log.w(TAG, "onDisconnect")
            ToastUtil.showDebugToast("onDisconnect")
            stop()
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            stop()
        }
    }

    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        senderClient = AudioSenderWebSocket(uri, connectionListener, ConstantRetry(10, 2000)).also {
            senderHandler = AudioSenderWebSocketHandler(it)
            it.initHandler(senderHandler)
            it.connect()
        }

        micRecorder = MicRecorder(AudioActivity.audioEncoderCodec, object : MicRecorder.RecordCallback {
            override fun onRecording(data: ByteArray) {
                recAudioQueue.offer(data)
                if (BuildConfig.DEBUG) LogContext.log.d(TAG, "mic rec data[${data.size}] queue=${recAudioQueue.size}")
            }

            override fun onStop(stopResult: Boolean) {
            }
        }, defaultAudioType).apply { startRecord() }
    }

    private fun sendRecAudioThread() {
        ioScope.launch {
            while (true) {
                ensureActive()
                runCatching {
//                    LogContext.log.i(ITAG, "PCM[${pcmData.size}] to be sent.")
                    recAudioQueue.poll()?.let { senderHandler?.sendAudioToServer(it) }
                    delay(10)
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    private fun startPlayThread() {
        LogContext.log.i(TAG, "Start decodeThread()")
        ioScope.launch {
            while (true) {
                ensureActive()
                receiveAudioQueue.poll()?.let { audioPlayer?.play(it) }
                delay(10)
            }
        }
    }

    fun stop() {
        ioScope.cancel()
        audioPlayer?.release()
        micRecorder?.stopRecord()
        senderClient?.disconnectManually()
        senderClient?.release()
    }
}