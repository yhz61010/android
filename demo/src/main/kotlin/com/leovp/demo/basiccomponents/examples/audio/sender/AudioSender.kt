package com.leovp.demo.basiccomponents.examples.audio.sender

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import com.leovp.android.exts.toast
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.demo.BuildConfig
import com.leovp.demo.basiccomponents.examples.audio.AudioActivity
import com.leovp.demo.basiccomponents.examples.audio.SuspendTeardownGate
import com.leovp.demo.basiccomponents.examples.audio.containsJob
import com.leovp.demo.basiccomponents.examples.audio.receiver.AudioReceiver.Companion.defaultAudioType
import com.leovp.demo.basiccomponents.examples.audio.sender.base.AudioSenderWebSocket
import com.leovp.demo.basiccomponents.examples.audio.sender.base.AudioSenderWebSocketHandler
import com.leovp.demo.basiccomponents.examples.audio.teardownSteps
import com.leovp.log.LogContext
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioSender {
    companion object {
        private const val TAG = "AudioSender"
        private const val AUDIO_QUEUE_CAPACITY = 64
    }

    private var ctx: Context? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * Teardown runs here rather than on [ioScope]: stop() cancel-and-joins ioScope itself, and
     * the netty listener callbacks that trigger it are non-suspend interface methods that cannot
     * simply be made suspend. The handler is what keeps a failed teardown off the platform
     * default handler.
     *
     * Deliberately never cancelled - cancelling it from stop() would cancel the very coroutine
     * running stop(), which is the situation this separate scope exists to avoid.
     */
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                LogContext.log.e(TAG, "Audio sender teardown failed", error)
            }
    )

    /** Makes the Activity path and the netty callback path share one teardown. */
    private val teardownGate = SuspendTeardownGate()

    /**
     * Set before teardown starts, checked by everything that builds a new resource - once before
     * building it and once more after publishing it.
     *
     * The one-shot gate guarantees teardown runs once; it cannot guarantee nobody builds new
     * resources afterwards. Two things still can: start() builds a MicRecorder after launching
     * connect(), and BaseNettyClient.doRetry() can reconnect and call onConnected() while
     * teardown is in flight. Either would leave an AudioRecord or an AudioTrack that the
     * already-consumed gate refuses to release. The check before building turns most of them
     * away; the re-check after publishing catches a stop() that ran in between and read the
     * field as null - the publisher then waits for the gate and releases what it built. Both
     * releases are idempotent, so it does not matter if the gate saw the resource after all.
     */
    private val stopping = AtomicBoolean(false)

    // Volatile where stop() reads them. Publishers write a field and then read `stopping`; stop()
    // writes `stopping` and then reads the fields. With both sides volatile at least one of them
    // sees the other's write, so a resource is released by the gate or by the re-check, never by
    // neither.
    @Volatile
    private var senderClient: AudioSenderWebSocket? = null
    private var senderHandler: AudioSenderWebSocketHandler? = null

    @Volatile
    private var micRecorder: MicRecorder? = null

    @Volatile
    private var audioPlayer: AudioPlayer? = null

    private val recAudioQueue = Channel<ByteArray>(AUDIO_QUEUE_CAPACITY)
    private val receiveAudioQueue = Channel<ByteArray>(AUDIO_QUEUE_CAPACITY)

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            if (stopping.get()) return
            LogContext.log.i(TAG, "onConnected")
            ctx?.toast("onConnected", debug = true)
            val player = AudioPlayer(
                ctx!!,
                AudioActivity.audioDecoderInfo,
                defaultAudioType,
                usage = AudioActivity.AUDIO_ATTR_USAGE,
                contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
            )
            audioPlayer = player
            if (stopping.get()) {
                // stop() ran between the check above and this assignment and saw no player.
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    player.releaseAndJoin()
                }
                return
            }
            sendRecAudioThread()
            startPlayThread()
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData Length[${audioData.size}]")
            receiveAudioQueue.trySend(audioData)
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w(TAG, "onDisconnect")
            ctx?.toast("onDisconnect", debug = true)
            // Not on ioScope: stop() cancel-and-joins it. And not blocking here either - this is
            // a netty event loop thread, and stop() waits for that same client to release.
            cleanupScope.launch { stop() }
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            // onFailed() is also called synchronously from inside connect(), which runs on
            // ioScope - one more reason teardown must not run on the caller's context.
            cleanupScope.launch { stop() }
        }
    }

    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        ioScope.launch {
            // Assign before connect(), not through `also { }` on the way out: connect() parks for
            // up to CONNECTION_TIMEOUT_IN_MILLS and calls onFailed() from inside itself, so the
            // trailing assignment leaves senderClient null for exactly the window in which stop()
            // needs it to end the retry chain and release the client.
            val client =
                AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000))
            senderClient = client
            senderHandler = AudioSenderWebSocketHandler(client)
            client.initHandler(senderHandler)
            client.connect()
        }

        // Before the constructor, not after: constructing a MicRecorder opens the microphone, and
        // a teardown already in flight would never see this instance to close it again.
        if (stopping.get()) return
        val recorder = MicRecorder(
            AudioActivity.audioEncoderInfo,
            object : MicRecorder.RecordCallback {
                override fun onRecording(data: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
                    val queued = recAudioQueue.trySend(data).isSuccess
                    if (BuildConfig.DEBUG) {
                        LogContext.log.d(TAG, "mic rec data[${data.size}] queued=$queued")
                    }
                }

                override fun onStop(stopResult: Boolean) {
                }
            },
            defaultAudioType,
            // Two-way voice: keep the VoIP capture chain. The library default
            // (MIC, no effects) is meant for record-to-file playback, and using it
            // here would let the far end hear its own echo.
            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            enableAdvancedFeatures = true
        ).apply { startRecord() }
        micRecorder = recorder
        // Re-check after publishing: a stop() that ran while the recorder was being built read
        // micRecorder as null and has consumed the gate, so nothing else will ever stop it.
        if (stopping.get()) {
            cleanupScope.launch {
                teardownGate.awaitCompletion()
                recorder.stopRecordAndJoin()
            }
        }
    }

    private fun sendRecAudioThread() {
        ioScope.launch {
            // The receive suspension point sits outside runCatching, so cancelling the scope - or
            // closing the channel in stop() - ends the worker, while a single failed send only
            // drops that frame.
            for (data in recAudioQueue) {
                runCatching {
                    // LogContext.log.i(ITAG, "PCM[${data.size}] to be sent.")
                    senderHandler?.sendAudioToServer(data)
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    private fun startPlayThread() {
        LogContext.log.i(TAG, "Start decodeThread()")
        ioScope.launch(Dispatchers.IO) {
            for (data in receiveAudioQueue) {
                audioPlayer?.play(data)
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
    suspend fun stop() {
        // Descendant check, not identity: ioScope.launch() children never equal the scope's own
        // Job, so `caller !== ioScope's Job` would pass for exactly the callers that deadlock.
        // Same shape as StreamPlayerStopper.stopAndJoin(), which StreamPlayerStopperTest covers.
        val ownerJob = ioScope.coroutineContext[Job]
        val callerJob = currentCoroutineContext()[Job]
        require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
            "stop() must be called from outside AudioSender's own scope"
        }
        // Before the gate, not inside it: followers never enter the block, and start() may still
        // be on its way to building a MicRecorder that this teardown would never see.
        stopping.set(true)
        teardownGate.run {
            teardownSteps(
                // Disconnect first, before anything slow. handleConnectFailure() calls onFailed
                // synchronously and then doRetry(), so the client can reconnect ~2s later and
                // hand onConnected() a fresh AudioPlayer that this one-shot gate would refuse to
                // release.
                //
                // Not what ends the retry chain on its own: on a client that has not reached
                // CONNECTING - the window where start()'s coroutine is still on its way to
                // connect() - disconnectManually() returns at its UNINITIALIZED guard and sets
                // nothing. What closes that window is `stopping`, which turns onConnected()
                // away, and the last step's release() calling stopRetryHandler(). This step is
                // what makes the common case cheap, not what makes the race safe.
                //
                // release() only reaches stopRetryHandler() past its own UNINITIALIZED guard,
                // and it does: there is no suspension point between `senderClient = client` and
                // the CONNECTING transition inside connect(), and start()'s job is
                // cancel-and-joined before the last step runs, so a non-null senderClient is
                // never UNINITIALIZED by then. A suspension point added to that stretch would
                // reopen a NioEventLoopGroup leak - the group is built in the constructor.
                { senderClient?.disconnectManually() },
                // Stop producing new frames.
                { micRecorder?.stopRecordAndJoin() },
                // Close the channels so a late netty callback's trySend() fails instead of
                // parking frames behind a consumer that is about to exit. One close per step,
                // like every other step here: two in one lambda would share a try again.
                { recAudioQueue.close() },
                { receiveAudioQueue.close() },
                // Stop the track before joining, not after: the same order
                // StreamPlayerStopper.prepareStop() uses. It releases nothing, so
                // releaseAndJoin() below still does the real teardown. Defensive here rather
                // than required - this player is always OPUS, so its own track is never played
                // and stop() returns at the PLAYSTATE_STOPPED guard (spec 15.1).
                //
                // Its own step, not merged with the join below: teardownSteps() exists so one
                // failing step does not skip the rest, and skipping the join is the one thing
                // that must never happen - releaseAndJoin() would then run while the workers
                // are still calling play().
                { audioPlayer?.stop() },
                { ownerJob?.cancelAndJoin() },
                { audioPlayer?.releaseAndJoin() },
                // Last: release() shuts the EventLoopGroups down, which the workers above may
                // still need a channel from.
                { senderClient?.release() }
            )
        }
    }
}
