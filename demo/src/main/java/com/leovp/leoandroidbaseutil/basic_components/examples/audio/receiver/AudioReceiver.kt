package com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver

import android.content.Context
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

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioReceiver {
    companion object {
        private const val TAG = "AudioReceiver"
    }

    private lateinit var receiverServer: AudioReceiverWebSocket
    private lateinit var receiverHandler: AudioReceiverWebSocketHandler

    private var pcmPlayer: PcmPlayer? = null
    private var micRecorder: MicRecorder? = null

    private val ioScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var startRecording = false

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(TAG, "onStarted")
            ToastUtil.showDebugToast("onStarted")
        }

        override fun onStopped() {
            LogContext.log.i(TAG, "onStop")
            ToastUtil.showDebugToast("onStop")
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientConnected: ${clientChannel.remoteAddress()}")
        }

        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?) {
            if (!startRecording) {
                startMicRecording(netty, clientChannel)
            }
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData from ${clientChannel.remoteAddress()} Length=${audioData.size}")
            ioScope.launch {
                runCatching {
                    ensureActive()

//                    val readBuffer = ByteArray(2560)
//                    val arrayInputStream = ByteArrayInputStream(audioData)
//                    val inputStream = InflaterInputStream(arrayInputStream)
//                    val read = inputStream.read(readBuffer)
//                    val originalPcmData = readBuffer.copyOf(read)

                    pcmPlayer?.play(audioData)
                }.onFailure { it.printStackTrace() }
            }
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientDisconnected: ${clientChannel.remoteAddress()}")
            stopRecordingAndPlaying()
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            stopRecordingAndPlaying()
        }

        private fun startMicRecording(netty: BaseNettyServer, clientChannel: Channel) {
            startRecording = true
            micRecorder = MicRecorder(AudioActivity.audioEncoderCodec, object : MicRecorder.RecordCallback {
                override fun onRecording(pcmData: ByteArray, st: Long, ed: Long) {
                    ioScope.launch {
                        runCatching {
                            ensureActive()
//                                LogContext.log.i(TAG, "Rec pcm[${pcmData.size}]")
//                            val targetOs = ByteArrayOutputStream(pcmData.size)
//                            DeflaterOutputStream(targetOs).use {
//                                it.write(pcmData)
//                                it.flush()
//                                it.finish()
//                            }
//                            val compressedData = targetOs.toByteArray()
                            netty.executeCommand(clientChannel, pcmData, false)
                        }.onFailure { it.printStackTrace() }
                    }
                }

                override fun onStop(stopResult: Boolean) {
                }
            }).apply { startRecord() }
        }
    }

    private fun stopRecordingAndPlaying() {
        // Please initialize AudioTrack with sufficient buffer, or else, it will crash when you release it.
        // Please check the initializing of AudioTrack in [PcmPlayer]
        //
        // And you must release AudioTrack first, otherwise, you will crash due to following exception:
        // releaseBuffer() track 0xde4c9100 disabled due to previous underrun, restarting
        // AudioTrackShared: Assertion failed: !(stepCount <= mUnreleased && mUnreleased <= mFrameCount)
        // Fatal signal 6 (SIGABRT), code -6 in tid 26866 (DefaultDispatch)
        pcmPlayer?.release()

        micRecorder?.stopRecord()
        startRecording = false
    }

    fun startServer(ctx: Context) {
        pcmPlayer = PcmPlayer(ctx, AudioActivity.audioPlayCodec, 5)

        receiverServer = AudioReceiverWebSocket(10020, connectionListener)
        receiverHandler = AudioReceiverWebSocketHandler(receiverServer)
        receiverServer.initHandler(receiverHandler)
        receiverServer.startServer()
    }

    fun stopServer() {
        ioScope.cancel()
        stopRecordingAndPlaying()
        if (::receiverServer.isInitialized) receiverServer.stopServer()
    }
}