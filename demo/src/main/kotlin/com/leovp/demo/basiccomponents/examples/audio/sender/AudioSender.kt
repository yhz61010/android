package com.leovp.demo.basiccomponents.examples.audio.sender

import android.annotation.SuppressLint
import android.content.Context
import com.leovp.android.exts.toast
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.demo.BuildConfig
import com.leovp.demo.basiccomponents.examples.audio.AudioActivity
import com.leovp.demo.basiccomponents.examples.audio.receiver.AudioReceiver.Companion.defaultAudioType
import com.leovp.demo.basiccomponents.examples.audio.sender.base.AudioSenderWebSocket
import com.leovp.demo.basiccomponents.examples.audio.sender.base.AudioSenderWebSocketHandler
import com.leovp.log.LogContext
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

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
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(64)

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.i(TAG, "onConnected")
            ctx?.toast("onConnected", debug = true)
            audioPlayer = AudioPlayer(ctx!!, AudioActivity.audioDecoderInfo, defaultAudioType)
            sendRecAudioThread()
            startPlayThread()
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData Length[${audioData.size}]")
            receiveAudioQueue.offer(audioData)
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w(TAG, "onDisconnect")
            ctx?.toast("onDisconnect", debug = true)
            stop()
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            stop()
        }
    }

    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        ioScope.launch {
            senderClient = AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000)).also {
                senderHandler = AudioSenderWebSocketHandler(it)
                it.initHandler(senderHandler)
                it.connect()
            }
        }

        micRecorder = MicRecorder(
            AudioActivity.audioEncoderInfo,
            object : MicRecorder.RecordCallback {
                override fun onRecording(data: ByteArray) {
                    recAudioQueue.offer(data)
                    if (BuildConfig.DEBUG) LogContext.log.d(TAG, "mic rec data[${data.size}] queue=${recAudioQueue.size}")
                }

                override fun onStop(stopResult: Boolean) {
                }
            },
            defaultAudioType
        ).apply { startRecord() }
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
        audioPlayer?.release()
        micRecorder?.stopRecord()
        ioScope.launch {
            senderClient?.disconnectManually()
            senderClient?.release()
        }
        ioScope.cancel()
    }
}
