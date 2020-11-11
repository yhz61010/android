package com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver

import android.content.Context
import com.leovp.androidbase.exts.asByteAndForceToBytes
import com.leovp.androidbase.exts.compress
import com.leovp.androidbase.exts.decompress
import com.leovp.androidbase.exts.toBytesLE
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.audio.player.PcmPlayer
import com.leovp.audio.recorder.MicRecorder
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.AudioActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.base.AudioReceiverWebSocket
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.base.AudioReceiverWebSocketHandler
import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.ServerConnectListener
import io.netty.channel.Channel
import kotlinx.coroutines.*
import java.util.concurrent.ArrayBlockingQueue

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioReceiver {
    companion object {
        private const val TAG = "AudioReceiver"
    }

    private var receiverServer: AudioReceiverWebSocket? = null
    private var receiverHandler: AudioReceiverWebSocketHandler? = null

    private var pcmPlayer: PcmPlayer? = null
    private var micRecorder: MicRecorder? = null

    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(10)
    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(10)

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        private var nettyServer: BaseNettyServer? = null
        private var clientChannel: Channel? = null

        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(TAG, "onStarted")
            ToastUtil.showDebugToast("onStarted")
        }

        override fun onStopped() {
            LogContext.log.i(TAG, "onStop")
            ToastUtil.showDebugToast("onStop")
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            nettyServer = netty
            this.clientChannel = clientChannel
            LogContext.log.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientConnected: ${clientChannel.remoteAddress()}")
            startMicRecording()
            playAudioThread()
            sendRecAudioThread()
        }

        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?) {
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData Length=${audioData.size} from ${clientChannel.remoteAddress()}")
            receiveAudioQueue.offer(audioData)
        }

        private fun playAudioThread() {
            ioScope.launch {
                while (true) {
                    ensureActive()
                    receiveAudioQueue.poll()?.let { pcmPlayer?.play(it.decompress()) }
                    delay(10)
                }
            }
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientDisconnected: ${clientChannel.remoteAddress()}")
            stopServer()
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            stopServer()
        }

        private fun startMicRecording() {
            micRecorder = MicRecorder(AudioActivity.audioEncoderCodec, object : MicRecorder.RecordCallback {
                override fun onRecording(pcmData: ByteArray, st: Long, ed: Long) {
                    recAudioQueue.offer(pcmData)
                }

                override fun onStop(stopResult: Boolean) {
                }
            }).apply { startRecord() }
        }

        private fun sendRecAudioThread() {
            ioScope.launch {
                while (true) {
                    ensureActive()
                    runCatching {
                        recAudioQueue.poll()?.let { comp ->
//                                LogContext.log.i(TAG, "Rec pcm[${pcmData.size}]")
                            comp.compress().let { pcm ->
                                val cmd = 1.asByteAndForceToBytes()
                                val protoVer = 1.asByteAndForceToBytes()

                                val contentLen = (cmd.size + protoVer.size + pcm.size).toBytesLE()
                                val command = ByteUtil.mergeBytes(contentLen, cmd, protoVer, pcm)
                                nettyServer?.executeCommand(clientChannel!!, command, false)
                            }
                        }
                        delay(10)
                    }.onFailure { it.printStackTrace() }
                }
            }
        }
    }

    fun startServer(ctx: Context) {
        pcmPlayer = PcmPlayer(ctx, AudioActivity.audioPlayCodec, 1)

        receiverServer = AudioReceiverWebSocket(10020, connectionListener).also {
            receiverHandler = AudioReceiverWebSocketHandler(it)
            it.initHandler(receiverHandler)
            it.startServer()
        }
    }

    fun stopServer() {
        ioScope.cancel()
        // Please initialize AudioTrack with sufficient buffer, or else, it will crash when you release it.
        // Please check the initializing of AudioTrack in [PcmPlayer]
        //
        // And you must release AudioTrack first, otherwise, you will crash due to following exception:
        // releaseBuffer() track 0xde4c9100 disabled due to previous underrun, restarting
        // AudioTrackShared: Assertion failed: !(stepCount <= mUnreleased && mUnreleased <= mFrameCount)
        // Fatal signal 6 (SIGABRT), code -6 in tid 26866 (DefaultDispatch)
        pcmPlayer?.release()
        micRecorder?.stopRecord()
        receiverServer?.stopServer()
    }
}