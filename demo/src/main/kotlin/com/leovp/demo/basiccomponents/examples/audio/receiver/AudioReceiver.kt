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
import com.leovp.demo.basiccomponents.examples.audio.SuspendTeardownGate
import com.leovp.demo.basiccomponents.examples.audio.containsJob
import com.leovp.demo.basiccomponents.examples.audio.receiver.base.AudioReceiverWebSocket
import com.leovp.demo.basiccomponents.examples.audio.receiver.base.AudioReceiverWebSocketHandler
import com.leovp.demo.basiccomponents.examples.audio.teardownSteps
import com.leovp.log.LogContext
import io.netty.channel.Channel
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
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

    // Volatile where stopServer() reads them - see AudioSender for the pairing with `stopping`.
    @Volatile
    private var receiverServer: AudioReceiverWebSocket? = null
    private var receiverHandler: AudioReceiverWebSocketHandler? = null

    @Volatile
    private var audioPlayer: AudioPlayer? = null

    @Volatile
    private var micRecorder: MicRecorder? = null
    // private var micOs: BufferedOutputStream? = null
    // private var rcvOs: BufferedOutputStream? = null

    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(10)
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(64)

    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * Teardown runs here rather than on [ioScope]: stopServer() terminates ioScope itself, and the
     * netty listener callbacks that trigger it are non-suspend interface methods that cannot
     * simply be made suspend. The handler is what keeps a failed teardown off the platform
     * default handler.
     *
     * Deliberately never cancelled - cancelling it from stopServer() would cancel the very
     * coroutine running stopServer(), which is the situation this separate scope exists to avoid.
     */
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                LogContext.log.e(TAG, "Audio receiver teardown failed", error)
            }
    )

    /** Makes the Activity path and the netty callback path share one teardown. */
    private val teardownGate = SuspendTeardownGate()

    /**
     * Set before teardown starts, checked by everything that builds a new resource - once before
     * building it and once more after publishing it.
     *
     * The one-shot gate guarantees teardown runs once; it cannot guarantee nobody builds new
     * resources afterwards. Two things still can: startServer() builds the player before the
     * server is up, and onClientConnected() builds the recorder and both workers. Either would
     * leave an AudioRecord or an AudioTrack that the already-consumed gate refuses to release.
     * The check before building turns most of them away; the re-check after publishing catches a
     * stopServer() that ran in between and read the field as null - the publisher then waits for
     * the gate and releases what it built. Both releases are idempotent, so it does not matter
     * if the gate saw the resource after all.
     */
    private val stopping = AtomicBoolean(false)

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
            if (stopping.get()) return
            nettyServer = netty
            this.clientChannel = clientChannel
            LogContext.log.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientConnected: ${clientChannel.remoteAddress()}", debug = true)
            val recorder = startMicRecording()
            micRecorder = recorder
            if (stopping.get()) {
                // stopServer() ran between the check above and this assignment and saw no
                // recorder, so nothing else will ever stop it.
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    recorder.stopRecordAndJoin()
                }
                return
            }
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
            cleanupScope.launch { stopServer() }
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            cleanupScope.launch { stopServer() }
        }

        /** Builds and starts the recorder; the caller publishes it and re-checks `stopping`. */
        private fun startMicRecording(): MicRecorder {
            return MicRecorder(
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
         * Blocks on the queue rather than polling it every 10ms. Polling capped the drain at
         * about 100 items a second whatever the arrival rate, so a burst could only be worked
         * off at that speed and the latency it created was never recovered - and in between,
         * the loop woke 100 times a second to find nothing.
         *
         * Still an ArrayBlockingQueue, unlike [AudioSender]'s coroutine channels. That is a
         * difference in migration order, not a design decision - an import alias would settle
         * the collision with io.netty.channel.Channel that this file has. What makes the two
         * equivalent for teardown is runInterruptible below: without it a parked take() would
         * outlive the scope's cancellation and hang the join in stopServer().
         */
        private fun sendRecAudioThread() {
            ioScope.launch {
                while (true) {
                    ensureActive()
                    // take() outside runCatching, and through runInterruptible: the call blocks
                    // uninterruptibly, so a plain take() would keep this coroutine parked on a
                    // Dispatchers.IO thread after the scope is cancelled until another item
                    // happened to arrive - which, after stopServer(), never comes.
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

    suspend fun startServer(ctx: Context) {
        this.ctx = ctx
        // Before the player is built, not after: a teardown already in flight would never see
        // this AudioTrack, and the consumed gate cannot release it later.
        if (stopping.get()) return
        val player = AudioPlayer(
            ctx,
            AudioActivity.audioDecoderInfo,
            defaultAudioType,
            usage = AudioActivity.AUDIO_ATTR_USAGE,
            contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
        )
        audioPlayer = player
        // micOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "mic.aac")))
        // rcvOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "rcv.aac")))

        // Assign before starting, not through `also { }` on the way out: onStarted() and the first
        // onClientConnected() can fire from inside startServer(), and stopServer() needs a non-null
        // receiverServer to have anything to stop. Back when startServer() blocked until the server
        // stopped, that assignment never happened at all while the server was up.
        val server = AudioReceiverWebSocket(10020, connectionListener)
        receiverServer = server
        receiverHandler = AudioReceiverWebSocketHandler(server)
        server.initHandler(receiverHandler)
        try {
            server.startServer()
        } finally {
            // Re-check after publishing, not only before building: a stopServer() that ran in
            // between read both fields as null and has consumed the gate, so the port and the
            // track would otherwise stay held for good. Wait for that teardown first, so this
            // release never overlaps its steps; both calls are then idempotent against what it
            // may already have done - BaseNettyServer.stopServer() returns false for a stopped
            // server.
            //
            // In a finally: the only exception startServer() lets out is the caller's
            // CancellationException, and a cancelled caller has published both fields just the
            // same. Without this, the compensation would hang on the caller's scope staying
            // alive - exactly the dependency this re-check exists to remove.
            if (stopping.get()) {
                // On cleanupScope, not inline: this waits for a teardown that is still running,
                // and the caller's scope is the Activity's. Inline, a cancelled caller would skip
                // both releases and leave the port and the AudioTrack held - the same reason
                // AudioSender routes its two compensation paths here. Through teardownSteps() for
                // the same reason the teardown itself is: stopServer() throwing must not skip the
                // track.
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    teardownSteps(
                        { server.stopServer() },
                        { player.releaseAndJoin() }
                    )
                }
            }
        }
    }

    /**
     * Cancel-and-joins [ioScope], so it must never be called from ioScope or any of its
     * children. All current call sites satisfy that: the two netty callbacks go through
     * [cleanupScope], and AudioActivity.onStop() uses the Activity's own scope.
     *
     * Concurrent callers share one teardown through [teardownGate] and observe the same result.
     */
    suspend fun stopServer() {
        // micOs?.closeQuietly()
        // rcvOs?.closeQuietly()
        //
        // Descendant check, not identity - see AudioSender.stop() for why.
        val ownerJob = ioScope.coroutineContext[Job]
        val callerJob = currentCoroutineContext()[Job]
        require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
            "stopServer() must be called from outside AudioReceiver's own scope"
        }
        // Before the gate: onClientConnected() builds the MicRecorder and both workers, and the
        // server is only stopped in the last step below, so a client arriving mid-teardown would
        // otherwise hand this instance resources the consumed gate can no longer release.
        stopping.set(true)
        teardownGate.run {
            teardownSteps(
                { micRecorder?.stopRecordAndJoin() },
                // Stop the track before joining, not after: the same order
                // StreamPlayerStopper.prepareStop() uses. It releases nothing, so
                // releaseAndJoin() below still does the real teardown. Defensive here rather
                // than required - this player is always OPUS, so its own track is never played
                // and stop() returns at the PLAYSTATE_STOPPED guard (spec 15.1).
                //
                // Its own step, not merged with the join below - see AudioSender.stop().
                { audioPlayer?.stop() },
                { ownerJob?.cancelAndJoin() },
                // Please initialize AudioTrack with sufficient buffer, or else, it will crash
                // when you release it. Releasing while a worker still writes gave:
                // releaseBuffer() track 0xde4c9100 disabled due to previous underrun, restarting
                // AudioTrackShared: Assertion failed: !(stepCount <= mUnreleased && mUnreleased
                // <= mFrameCount)
                // Fatal signal 6 (SIGABRT), code -6 in tid 26866 (DefaultDispatch)
                // That note applies to the PCM path; the join above is what makes it moot here.
                { audioPlayer?.releaseAndJoin() },
                // Last, not first: stopServer() shuts both EventLoopGroups down, and the workers
                // above may still be sending through a channel. A client arriving before this
                // runs is turned away by the `stopping` flag instead.
                { receiverServer?.stopServer() }
            )
        }
    }
}
