package com.leovp.demo.basiccomponents.examples.audio.receiver

import android.content.Context
import android.media.MediaRecorder
import com.leovp.android.exts.toast
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.audio.base.AudioType
import com.leovp.basenetty.framework.server.BaseNettyServer
import com.leovp.basenetty.framework.server.ServerConnectListener
import com.leovp.demo.BuildConfig
import com.leovp.demo.basiccomponents.examples.audio.AudioActivity
import com.leovp.demo.basiccomponents.examples.audio.receiver.base.AudioReceiverWebSocket
import com.leovp.demo.basiccomponents.examples.audio.receiver.base.AudioReceiverWebSocketHandler
import com.leovp.log.LogContext
import io.netty.channel.Channel
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioReceiver {
    companion object {
        private const val TAG = "AudioReceiver"
        val defaultAudioType = AudioType.OPUS
    }

    private var ctx: Context? = null

    private var receiverServer: AudioReceiverWebSocket? = null
    private var receiverHandler: AudioReceiverWebSocketHandler? = null

    private var audioPlayer: AudioPlayer? = null
    private var micRecorder: MicRecorder? = null
    // private var micOs: BufferedOutputStream? = null
    // private var rcvOs: BufferedOutputStream? = null

    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(10)
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(64)

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        private var nettyServer: BaseNettyServer? = null
        private var clientChannel: Channel? = null

        // private val opusFile by lazy { ctx!!.createFile("audio_rcv.opus") }
        // private var opusOs: BufferedOutputStream? = null
        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(TAG, "onStarted")
            ctx?.toast("onStarted", debug = true)
            // opusOs = BufferedOutputStream(FileOutputStream(opusFile))
        }

        override fun onStopped() {
            LogContext.log.i(TAG, "onStop")
            ctx?.toast("onStop", debug = true)

            // opusOs?.flush()
            // opusOs?.close()
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            nettyServer = netty
            this.clientChannel = clientChannel
            LogContext.log.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientConnected: ${clientChannel.remoteAddress()}", debug = true)
            startMicRecording()
            sendRecAudioThread()
            startPlayThread()
        }

        override fun onReceivedData(
            netty: BaseNettyServer,
            clientChannel: Channel,
            data: Any?,
            action: Int
        ) {
            val audioData = data as ByteArray
            // LogContext.log.i(TAG, "onReceivedData Length=${audioData.size}
            // Queue=${receiveAudioQueue.size} " +
            //     "from ${clientChannel.remoteAddress()}") // hex=${audioData.toHexStringLE()}
            // runCatching { opusOs?.write(data) }.onFailure { it.printStackTrace() }
            receiveAudioQueue.offer(audioData)
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientDisconnected: ${clientChannel.remoteAddress()}", debug = true)
            stopServer()
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            stopServer()
        }

        private fun startMicRecording() {
            micRecorder = MicRecorder(
                AudioActivity.audioEncoderInfo,
                object : MicRecorder.RecordCallback {
                    override fun onRecording(
                        data: ByteArray,
                        isConfig: Boolean,
                        isKeyFrame: Boolean
                    ) {
                        recAudioQueue.offer(data)
                        if (BuildConfig.DEBUG) {
                            LogContext.log.d(
                                TAG,
                                "mic rec data[${data.size}] queue=${recAudioQueue.size}"
                            )
                        }
                        // runCatching { micOs?.write(data) }.onFailure { it.printStackTrace() }
                    }

                    override fun onStop(stopResult: Boolean) {
                    }
                },
                defaultAudioType,
                // Two-way voice: keep the VoIP capture chain. The library default
                // (MIC, no effects) is meant for record-to-file playback, and using
                // it here would let the far end hear its own echo.
                audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                enableAdvancedFeatures = true
            ).apply {
                startRecord()
            }
        }

        /**
         * Blocks on the queue rather than polling it every 10ms, which is what [AudioSender]
         * already does with the same queues. Polling capped the drain at about 100 items a
         * second whatever the arrival rate, so a burst could only be worked off at that speed
         * and the latency it created was never recovered - and in between, the loop woke 100
         * times a second to find nothing.
         */
        private fun sendRecAudioThread() {
            ioScope.launch {
                while (true) {
                    ensureActive()
                    // take() outside runCatching, and through runInterruptible: the call blocks
                    // uninterruptibly, so a plain take() would keep this coroutine parked on a
                    // Dispatchers.IO thread after ioScope.cancel() until another item happened to
                    // arrive - which, after stopServer(), never comes.
                    val pcmData = runInterruptible { recAudioQueue.take() }
                    runCatching {
                        // LogContext.log.i(TAG, "Rec pcm[${pcmData.size}]")
                        receiverHandler?.sendAudioToClient(clientChannel!!, pcmData)
                    }.onFailure { it.printStackTrace() }
                }
            }
        }

        /** @see sendRecAudioThread for why this blocks instead of polling. */
        private fun startPlayThread() {
            LogContext.log.i(TAG, "Start decodeThread()")
            ioScope.launch {
                while (true) {
                    ensureActive()
                    // Read before the safe call, not inside it: `audioPlayer?.play(queue.take())`
                    // never evaluates take() when audioPlayer is null, which would turn this into
                    // a tight loop on a dispatcher thread instead of a wait.
                    val audioData = runInterruptible { receiveAudioQueue.take() }
                    audioPlayer?.play(audioData)
                }
            }
        }
    }

    fun startServer(ctx: Context) {
        this.ctx = ctx
        audioPlayer = AudioPlayer(
            ctx,
            AudioActivity.audioDecoderInfo,
            defaultAudioType,
            usage = AudioActivity.AUDIO_ATTR_USAGE,
            contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
        )
        // micOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "mic.aac")))
        // rcvOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "rcv.aac")))

        receiverServer = AudioReceiverWebSocket(10020, connectionListener).also {
            receiverHandler = AudioReceiverWebSocketHandler(it)
            it.initHandler(receiverHandler)
            it.startServer()
        }
    }

    fun stopServer() {
        // micOs?.closeQuietly()
        // rcvOs?.closeQuietly()
        ioScope.cancel()
        // Please initialize AudioTrack with sufficient buffer, or else, it will crash when you
        // release it.
        //
        // And you must release AudioTrack first, otherwise, you will crash due to following
        // exception:
        // releaseBuffer() track 0xde4c9100 disabled due to previous underrun, restarting
        // AudioTrackShared: Assertion failed: !(stepCount <= mUnreleased && mUnreleased <=
        // mFrameCount)
        // Fatal signal 6 (SIGABRT), code -6 in tid 26866 (DefaultDispatch)
        audioPlayer?.release()
        micRecorder?.stopRecord()
        receiverServer?.stopServer()
    }
}
