package com.leovp.screencapture.screenrecord.base.strategies

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue
import android.util.Size
import android.view.Surface
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import java.nio.ByteBuffer
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One-shot MediaProjection recorder with a persistent OES input. Rotation resizes the existing
 * virtual display and replaces only the encoder output; screenshots read the same GL frame while
 * recording continues. Upgrading callers must create a new strategy and obtain a new projection
 * after [onStop] or [onRelease]; restarting a stopped projection is not supported.
 *
 * Lifecycle calls are serialized. Screenshot callbacks run on the recording delivery thread and
 * transfer bitmap ownership to the caller. Stop/release discard pending requests and recycle
 * bitmaps that have not reached a callback. Callbacks must return promptly without waiting for
 * lifecycle work. EGL retirement waits at most two seconds; a timeout or interrupt means cleanup
 * remains owned by the EGL thread, not that native release has completed. Such an instance must
 * never be reused; interruption is preserved. Asynchronous codec/GL/output failures stop accepting
 * work immediately and invoke [ScreenDataListener.onError] once, on the main thread after native
 * retirement completes. That error callback may synchronously invoke [onRelease]. System projection
 * revocation is a normal stop. EGL/native behavior requires real-device validation.
 *
 * Per-encoder native operations and CSD updates are serialized against retirement. Queued delivery
 * rechecks the current session before invoking the business listener; a call already selected or
 * entered may finish after rotation/stop. Business listeners run without lifecycle/native locks,
 * so retirement never waits for those listeners while holding a resource lock.
 *
 * Author: Michael Leo
 * Date: 20-5-15 下午1:54
 */
class ScreenRecordMediaCodecStrategy private constructor(private val builder: Builder) :
    ScreenProcessor {

    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var eglSession: ProjectionEglSession? = null
    private val initAttempted = AtomicBoolean(false)
    private val releaseStarted = AtomicBoolean(false)
    private val failureGate = Any()
    private var terminalFailure: Throwable? = null
    private val failureNotified = AtomicBoolean(false)
    @Volatile
    private var terminal = false
    private val pendingBitmaps = ConcurrentLinkedQueue<Bitmap>()

    private class EncoderSession(val codec: MediaCodec) {
        val lock = ReentrantLock()
        @Volatile
        var active = true
    }

    @Volatile
    private var encoderSession: EncoderSession? = null

    @Volatile
    private var h26xEncoder: MediaCodec? = null
    private var videoEncoderLoop = AtomicBoolean(false)
    private val encoderStarted = AtomicBoolean(false)
    private var videoDataSendThread: HandlerThread? = null
    @Volatile
    private var videoDataSendHandler: Handler? = null
    private var codecCallbackThread: HandlerThread? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (terminal) return
            Handler(Looper.getMainLooper()).post {
                if (!terminal) {
                    runCatching { onRelease() }.onFailure {
                        LogContext.log.e(TAG, "Revoked projection cleanup failed", it)
                    }
                }
            }
        }
    }
    private val projectionCallbackLock = Any()
    private var projectionCallbackRegistered = false
    private val projectionStopped = AtomicBoolean(false)

    @Suppress("WeakerAccess")
    var vpsSpsPpsBuf: ByteArray? = null
        private set

    private var outputFormat: MediaFormat? = null

    private fun createMediaCodecCallback(session: EncoderSession) = object : MediaCodec.Callback() {
        private var beginPts: Long? = null

        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) = Unit

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            outputBufferId: Int,
            info: MediaCodec.BufferInfo
        ) {
            var failure: Throwable? = null
            var selectedFailure = false
            session.lock.withLock {
                if (!session.active || session !== encoderSession || terminal) return@withLock
                if (codec !== session.codec) return@withLock
                try {
                    codec.getOutputBuffer(outputBufferId)?.let { output ->
                        val firstPts = beginPts ?: info.presentationTimeUs.also { beginPts = it }
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        onSendAvcFrame(
                            session, output, info.flags, info.size,
                            info.presentationTimeUs - firstPts
                        )
                    }
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    failure = collectCleanupFailure(failure) {
                        codec.releaseOutputBuffer(outputBufferId, false)
                    }
                }
                failure?.let {
                    session.active = false
                    selectedFailure = selectFailure(it, session)
                }
            }
            if (selectedFailure) scheduleFailureCleanup(checkNotNull(failure))
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) =
            session.lock.withLock {
                if (session.active && session === encoderSession && !terminal) outputFormat = format
            }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            val selected = session.lock.withLock {
                if (!session.active || session !== encoderSession || terminal) return@withLock false
                if (codec !== session.codec) return@withLock false
                session.active = false
                selectFailure(e, session)
            }
            if (selected) scheduleFailureCleanup(e)
        }
    }

    class Builder(
        var width: Int,
        var height: Int,
        val dpi: Int,
        val mediaProjection: MediaProjection?,
        val screenDataListener: ScreenDataListener
    ) {
        var encodeType: EncodeType = EncodeType.H264
            private set
        var fps = 20F
            private set
        var bitrate = width * height
            private set
        var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            private set

        /**
         * Key-frame interval in seconds. Encoder frame rate is configured separately through
         * [setFps].
         */
        var iFrameInterval = 1
            private set
        var useGoogleEncoder = false
            private set

        fun setEncodeType(encodeType: EncodeType) = apply { this.encodeType = encodeType }

        /** Controls encoder frame rate; the removed `setKeyFrameRate()` was ignored. */
        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }
        fun setGoogleEncoder(useGoogleEncoder: Boolean) =
            apply { this.useGoogleEncoder = useGoogleEncoder }

        fun build(): ScreenRecordMediaCodecStrategy {
            LogContext.log.i(
                TAG,
                "encodeType=$encodeType width=$width height=$height dpi=$dpi fps=$fps " +
                    "bitrate=$bitrate bitrateMode=$bitrateMode " +
                    "iFrameInterval=$iFrameInterval"
            )
            return ScreenRecordMediaCodecStrategy(this)
        }
    }

    /**
     * This method must be called on main thread.
     */
    @Synchronized
    override fun onInit() {
        check(!terminal && initAttempted.compareAndSet(false, true)) {
            "This recorder is one-shot; create a new strategy and projection"
        }
        var callbackRegisteredDuringInit = false
        try {
            initializeEncoder()
            val session = ProjectionEglSession { requestEglFailure(it) }
            eglSession = session
            val projectionSurface = session.initialize(
                checkNotNull(inputSurface), builder.width, builder.height
            )
            callbackRegisteredDuringInit = registerProjectionCallback()
            virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
                "screen-record", builder.width, builder.height, builder.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, projectionSurface, null, null
            )
            initHandler()
        } catch (error: Throwable) {
            terminal = true
            rollbackFailedInit(error, callbackRegisteredDuringInit)
            throw error
        }
    }

    @SuppressLint("InlinedApi")
    private fun initializeEncoder() {
        val format = MediaFormat.createVideoFormat(
            when (builder.encodeType) {
                EncodeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                EncodeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            },
            builder.width,
            builder.height
        )

        with(format) {
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, builder.bitrate)
            setInteger(MediaFormat.KEY_BITRATE_MODE, builder.bitrateMode)
            // The frame rate tells the encoder how many
            // frames a second to spread KEY_BIT_RATE over; a number the display does not
            // actually deliver misallocates that budget across the frames it does.
            setInteger(MediaFormat.KEY_FRAME_RATE, builder.fps.toInt().coerceAtLeast(1))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, builder.iFrameInterval)
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setInteger(MediaFormat.KEY_LATENCY, 0)
            // }
            // Set the encoder priority to realtime.
            setInteger(MediaFormat.KEY_PRIORITY, 0x00)
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Actually, this key has been used in Android 6.0+ although it just has been opened as
            // of Android 10.
            @Suppress("unchecked")
            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
            // }
            // val profileLevelPair =
            // CodecUtil.getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)
            // // .firstOrNull { it.profile ==
            // MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline }
            // // .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
            // // .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain }
            // .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline }
            // //                .maxByOrNull { it.profile }
            // val usedProfile = profileLevelPair?.profile ?:
            // MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            // val usedLevel = profileLevelPair?.level ?: MediaCodecInfo.CodecProfileLevel.AVCLevel4
            //            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            //                LogContext.log.w(TAG, "KEY_PROFILE: $usedProfile")
            //                setInteger(MediaFormat.KEY_PROFILE, usedProfile)
            //            } else {
            //                LogContext.log.w(TAG, "KEY_PROFILE static: AVCProfileBaseline")
            // setInteger(MediaFormat.KEY_PROFILE,
            // MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            //            }
            //            setInteger(MediaFormat.KEY_COMPLEXITY, bitrateMode)
            //            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                // You must specify KEY_LEVEL on Android 6.0+
            //                // AVCLevel51
            //                // AVCLevel4
            //                LogContext.log.w(TAG, "KEY_LEVEL: $usedLevel")
            //                setInteger(MediaFormat.KEY_LEVEL, usedLevel)
            //            } else {
            //                LogContext.log.w(TAG, "KEY_LEVEL static: AVCLevel4")
            // setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            //            }
        }
        val createCodec = {
            if (builder.useGoogleEncoder) {
                when (builder.encodeType) {
                    EncodeType.H264 -> MediaCodec.createByCodecName("OMX.google.h264.encoder")
                    EncodeType.H265 -> {
                        val hevcEncoderName = getHevcCodec(encoder = true)[0].name
                        LogContext.log.i(TAG, "hevcEncoderName=$hevcEncoderName")
                        MediaCodec.createByCodecName(hevcEncoderName)
                    } // "c2.android.hevc.encoder"
                }
            } else {
                when (builder.encodeType) {
                    EncodeType.H264 ->
                        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    EncodeType.H265 ->
                        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                }
            }
        }
        val callbackThread = HandlerThread("scr-rec-codec").apply { start() }
        codecCallbackThread = callbackThread
        val callbackHandler = Handler(callbackThread.looper)
        val callbackSession = AtomicReference<EncoderSession>()
        fun callbackFor(codec: MediaCodec): MediaCodec.Callback =
            createMediaCodecCallback(EncoderSession(codec).also { callbackSession.set(it) })
        run {
            val encoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createCodec().also {
                    h26xEncoder = it
                    it.setCallback(callbackFor(it), callbackHandler)
                }
            } else {
                createLegacyCodec(callbackHandler, createCodec, ::callbackFor)
            }
            h26xEncoder = encoder
            encoderSession = callbackSession.get()
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            outputFormat = encoder.outputFormat
            inputSurface = encoder.createInputSurface()
        }
    }

    /**
     * The callback worker owns the codec until a successful rendezvous transfers it to the caller.
     * Timeout/interruption only abandons the handoff: native setCallback may ignore interruption,
     * and only that worker may release an unclaimed codec after its native operation has exited.
     */
    private fun createLegacyCodec(
        handler: Handler,
        createCodec: () -> MediaCodec,
        callbackFor: (MediaCodec) -> MediaCodec.Callback,
    ): MediaCodec {
        val abandoned = AtomicBoolean(false)
        val handoff = SynchronousQueue<Result<MediaCodec>>()
        val posted = handler.post {
            var codec: MediaCodec? = null
            var handedOff = false
            try {
                codec = createCodec()
                check(!abandoned.get()) { "Codec creation was abandoned" }
                codec.setCallback(callbackFor(codec))
                check(!abandoned.get()) { "Codec creation was abandoned" }
                handedOff = handoff.offer(
                    Result.success(codec), CALLBACK_SETUP_TIMEOUT_MS, TimeUnit.MILLISECONDS
                )
            } catch (error: Throwable) {
                if (!abandoned.get()) {
                    try {
                        handoff.offer(
                            Result.failure(error), CALLBACK_SETUP_TIMEOUT_MS, TimeUnit.MILLISECONDS
                        )
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        error.addSuppressed(interrupted)
                        LogContext.log.e(TAG, "Codec failure handoff was interrupted", error)
                    }
                }
            } finally {
                if (!handedOff) {
                    runCatching { codec?.release() }.onFailure {
                        LogContext.log.e(TAG, "Abandoned codec release failed", it)
                    }
                }
            }
        }
        check(posted) { "Codec callback thread rejected creation" }
        val result = try {
            handoff.poll(CALLBACK_SETUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            abandoned.set(true)
            Thread.currentThread().interrupt()
            throw error
        }
        if (result == null) {
            abandoned.set(true)
            throw TimeoutException("Timed out waiting for codec callback setup")
        }
        // This may throw an InterruptedException from the worker. It must not set caller flags.
        return result.getOrThrow()
    }

    /**
     * Attempts or queues every cleanup step and aggregates cleanup failures. The external EGL
     * retirement wait is bounded: timeout/interruption can return before native cleanup finishes,
     * while the queued EGL task retains ownership and completes all remaining steps. Callers must
     * not reuse this instance or independently free its former native resources. Interrupt status
     * is preserved; asynchronous failure notification still waits for actual retirement completion.
     * Owned resources are detached before release to prevent double frees; a failed projection
     * callback unregister remains eligible for retry because its registration flag is retained.
     */
    @Synchronized
    override fun onRelease() {
        synchronized(failureGate) { terminal = true }
        var failure = collectCleanupFailure(null) { unregisterProjectionCallback() }
        if (!releaseStarted.compareAndSet(false, true)) {
            failure?.let { throw it }
            return
        }
        videoEncoderLoop.set(false)
        val wasRecording = encoderStarted.getAndSet(false)
        failure = collectCleanupFailure(failure) { releaseHandler() }
        val retire = detachRetirement(wasRecording)
        val session = eglSession
        eglSession = null
        failure = collectCleanupFailure(failure) {
            if (session == null) {
                var retirementFailure: Throwable? = null
                try {
                    retire()
                } catch (error: Throwable) {
                    retirementFailure = error
                    throw error
                } finally {
                    onRetired(retirementFailure)
                }
            } else {
                session.release(retire, ::onRetired)
            }
        }
        failure?.let { throw it }
    }

    /** One-shot terminal stop; see [onRelease] for asynchronous retirement after a timeout. */
    override fun onStop() = onRelease()

    /** Captures every borrowed native resource before handing it to the EGL retirement task. */
    private fun detachRetirement(wasRecording: Boolean): () -> Unit {
        val display = virtualDisplay
        virtualDisplay = null
        val surface = inputSurface
        inputSurface = null
        val encoder = h26xEncoder
        h26xEncoder = null
        val session = encoderSession
        encoderSession = null
        val callbackThread = codecCallbackThread
        codecCallbackThread = null
        return {
            var failure: Throwable? = null
            val lock = session?.lock ?: ReentrantLock()
            lock.withLock {
                session?.active = false
                if (wasRecording) failure = collectCleanupFailure(failure) { encoder?.stop() }
                failure = collectCleanupFailure(failure) { display?.release() }
                failure = collectCleanupFailure(failure) { surface?.release() }
                failure = collectCleanupFailure(failure) { encoder?.release() }
            }
            if (projectionStopped.compareAndSet(false, true)) {
                failure = collectCleanupFailure(failure) { builder.mediaProjection?.stop() }
            }
            failure = collectCleanupFailure(failure) { stopCodecCallbackThread(callbackThread) }
            failure?.let { throw it }
        }
    }

    @Synchronized
    override fun onStart() {
        check(!terminal) { "A stopped projection session cannot be restarted" }
        if (videoEncoderLoop.get()) return
        val encoder = checkNotNull(h26xEncoder) { "You must initialize Video Encoder" }
        try {
            initHandler()
            encoder.start()
            encoderStarted.set(true)
            check(!terminal) { "Codec failed while starting" }
            videoEncoderLoop.set(true)
            eglSession?.setRecording(true)
        } catch (error: Throwable) {
            cleanupAfterInitFailure(error) { onRelease() }
            throw error
        }
    }

    override fun getVideoSize(): Size = Size(builder.width, builder.height)

    /**
     * Requests a key frame from the live encoder, serialized against native retirement.
     * Returns false when no recording session accepts work. Native request exceptions propagate
     * to the caller without changing the recording lifecycle.
     *
     * Migration: the removed public `h26xEncoder` property no longer lends the native codec to
     * callers. Replace direct key-frame `setParameters()` calls with this method; the strategy
     * exclusively owns native operations and release.
     */
    fun requestKeyFrame(): Boolean {
        val session = encoderSession ?: return false
        return session.lock.withLock {
            if (!session.active ||
                session !== encoderSession ||
                terminal ||
                !videoEncoderLoop.get()
            ) {
                return@withLock false
            }
            val parameters = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            session.codec.setParameters(parameters)
            true
        }
    }

    /**
     * This method must be called on main thread.
     */
    @Synchronized
    override fun changeOrientation() {
        check(!terminal && h26xEncoder != null) { "Initialize a live projection session first" }
        val wasRecording = videoEncoderLoop.getAndSet(false)
        try {
            val session = checkNotNull(eglSession)
            session.detachOutput()
            check(!terminal) { "Codec failed during rotation" }
            retireEncoder(wasRecording)
            check(!terminal) { "Codec failed during rotation" }
            val previousWidth = builder.width
            builder.width = builder.height
            builder.height = previousWidth
            initializeEncoder()
            check(!terminal) { "Codec failed during rotation" }
            session.resize(builder.width, builder.height)
            virtualDisplay?.resize(builder.width, builder.height, builder.dpi)
            session.replaceOutput(checkNotNull(inputSurface), builder.width, builder.height)
            vpsSpsPpsBuf = null
            if (wasRecording) {
                h26xEncoder?.start()
                encoderStarted.set(true)
                videoEncoderLoop.set(true)
                session.setRecording(true)
            }
        } catch (error: Throwable) {
            cleanupAfterInitFailure(error) { onRelease() }
            throw error
        }
    }

    private fun retireEncoder(wasRecording: Boolean) {
        encoderStarted.set(false)
        val previousEncoder = h26xEncoder
        h26xEncoder = null
        val previousSession = encoderSession
        encoderSession = null
        val previousSurface = inputSurface
        inputSurface = null
        var failure: Throwable? = null
        val lock = previousSession?.lock ?: ReentrantLock()
        lock.withLock {
            previousSession?.active = false
            if (wasRecording) failure = collectCleanupFailure(failure) { previousEncoder?.stop() }
            failure = collectCleanupFailure(failure) { previousEncoder?.release() }
            failure = collectCleanupFailure(failure) { previousSurface?.release() }
        }
        failure = collectCleanupFailure(failure) { releaseCodecCallbackThread() }
        failure?.let { throw it }
    }

    private fun initHandler() {
        releaseHandler()
        videoDataSendThread = HandlerThread("scr-rec-send").apply { start() }
        videoDataSendHandler = Handler(videoDataSendThread!!.looper)
    }

    private fun releaseHandler() {
        videoDataSendHandler?.removeCallbacksAndMessages(null)
        videoDataSendHandler = null
        videoDataSendThread?.quitSafely()
        videoDataSendThread = null
        while (true) (pendingBitmaps.poll() ?: break).recycle()
    }

    private fun rollbackFailedInit(
        originalError: Throwable,
        callbackRegisteredDuringInit: Boolean,
    ) {
        releaseStarted.set(true)
        cleanupAfterInitFailure(originalError) { releaseHandler() }
        if (callbackRegisteredDuringInit) {
            cleanupAfterInitFailure(originalError) { unregisterProjectionCallback() }
        }
        val retire = detachRetirement(wasRecording = false)
        val session = eglSession
        eglSession = null
        cleanupAfterInitFailure(originalError) {
            if (session == null) {
                var retirementFailure: Throwable? = null
                try {
                    retire()
                } catch (error: Throwable) {
                    retirementFailure = error
                    throw error
                } finally {
                    onRetired(retirementFailure)
                }
            } else {
                session.release(retire, ::onRetired)
            }
        }
    }

    private inline fun cleanupAfterInitFailure(originalError: Throwable, cleanup: () -> Unit) {
        try {
            cleanup()
        } catch (cleanupError: Throwable) {
            if (cleanupError !== originalError) originalError.addSuppressed(cleanupError)
        }
    }

    private inline fun collectCleanupFailure(
        previousFailure: Throwable?,
        cleanup: () -> Unit,
    ): Throwable? = try {
        cleanup()
        previousFailure
    } catch (cleanupError: Throwable) {
        if (previousFailure == null) {
            cleanupError
        } else {
            if (cleanupError !== previousFailure) previousFailure.addSuppressed(cleanupError)
            previousFailure
        }
    }

    private fun unregisterProjectionCallback() {
        synchronized(projectionCallbackLock) {
            if (!projectionCallbackRegistered) return
            builder.mediaProjection?.unregisterCallback(projectionCallback)
            projectionCallbackRegistered = false
        }
    }

    private fun releaseCodecCallbackThread() {
        val callbackThread = codecCallbackThread
        codecCallbackThread = null
        stopCodecCallbackThread(callbackThread)
    }

    private fun stopCodecCallbackThread(callbackThread: HandlerThread?) {
        callbackThread?.quitSafely()
        if (callbackThread != null && Thread.currentThread() !== callbackThread) {
            try {
                callbackThread.join(CALLBACK_THREAD_JOIN_TIMEOUT_MS)
                if (callbackThread.isAlive) {
                    LogContext.log.w(TAG, "Codec callback thread did not stop within timeout")
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                LogContext.log.e(TAG, "Interrupted while stopping codec callback thread", error)
            }
        }
    }

    private fun onSendAvcFrame(
        session: EncoderSession,
        bb: ByteBuffer,
        flags: Int,
        bufferSize: Int,
        presentationTimeUs: Long
    ) {
        //        var naluIndex = 4
        //        if (bb[2].toInt() == 0x01) {
        //            naluIndex = 3
        //        }

        //        val naluType = H264Util.getNaluType(bb.get(naluIndex))
        val bytes = ByteArray(bufferSize)
        bb.get(bytes)

        if (MediaCodec.BUFFER_FLAG_CODEC_CONFIG == flags) {
            vpsSpsPpsBuf = bytes.copyOf()
            when (builder.encodeType) {
                EncodeType.H264 -> LogContext.log.w(
                    TAG,
                    "Found SPS/PPS=${vpsSpsPpsBuf?.toHexString()}"
                )

                EncodeType.H265 -> LogContext.log.w(
                    TAG,
                    "Found VPS/SPS/PPS=${vpsSpsPpsBuf?.toHexString()}"
                )
            }
        }

        //        val naluTypeStr = when (naluType) {
        //            NAL_SPS -> {
        //                spsPpsBuf = bytes.copyOf()
        //                "SPS"
        //            }
        //            NAL_PPS -> {
        //                "PPS"
        //            }
        //            NAL_SLICE_IDR -> {
        //                "I"
        //            }
        //            else -> {
        //                "P"
        //            }
        //        }
        //        if (BuildConfig.DEBUG) {
        //            LogContext.log.d(
        //                TAG,
        // "$naluTypeStr:Len=${bytes.size}${if (NAL_SPS == naluType) "[" + bytes.toHexStringLE() +
        // "]" else ""}"
        //            )
        //        }

        videoDataSendHandler?.post {
            val current = session.active && session === encoderSession && !terminal
            if (!current || !videoEncoderLoop.get()) return@post
            try {
                builder.screenDataListener.onDataUpdate(bytes, flags, presentationTimeUs)
            } catch (error: Throwable) {
                requestEglFailure(error, session)
            }
        }
    }

    /**
     * Captures the current projection frame without interrupting recording. If no frame has
     * arrived, delivery waits for the next frame. Dimensions default to the current video size.
     * Stop, release, and rotation cancel requests that have not yet produced a bitmap. At most
     * four captures may be pending. Invalid dimensions and request overload throw synchronously;
     * an ordinary capture failure logs the error without invoking [result] or stopping recording.
     */
    @Synchronized
    override fun takeScreenshot(width: Int?, height: Int?, result: (bitmap: Bitmap) -> Unit) {
        check(!terminal && videoEncoderLoop.get()) { "Screenshots require an active recording" }
        val finalWidth = width ?: builder.width
        val finalHeight = height ?: builder.height
        require(finalWidth > 0 && finalHeight > 0) { "Screenshot dimensions must be positive" }
        checkNotNull(eglSession).screenshot(finalWidth, finalHeight) { bitmap ->
            pendingBitmaps.add(bitmap)
            val accepted = videoDataSendHandler?.post {
                if (pendingBitmaps.remove(bitmap)) {
                    if (terminal || !videoEncoderLoop.get()) {
                        bitmap.recycle()
                    } else {
                        try {
                            result(bitmap)
                        } catch (error: Exception) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            LogContext.log.e(TAG, "Screenshot callback failed", error)
                        } catch (error: Throwable) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            requestEglFailure(error)
                        }
                    }
                }
            } ?: false
            if (!accepted && pendingBitmaps.remove(bitmap)) bitmap.recycle()
        }
    }

    private fun selectFailure(error: Throwable, expected: EncoderSession? = null): Boolean =
        synchronized(failureGate) {
            if (terminal) return@synchronized false
            if (expected != null && expected !== encoderSession) return@synchronized false
            terminalFailure = error
            terminal = true
            videoEncoderLoop.set(false)
            true
        }

    private fun requestEglFailure(error: Throwable, expected: EncoderSession? = null) {
        if (selectFailure(error, expected)) scheduleFailureCleanup(error)
    }

    private fun scheduleFailureCleanup(error: Throwable) {
        LogContext.log.e(TAG, "Projection processing failure", error)
        Handler(Looper.getMainLooper()).post {
            runCatching { onRelease() }.onFailure {
                LogContext.log.e(TAG, "Projection failure cleanup failed", it)
            }
        }
    }

    /** Executed only after EGL and every borrowed native cleanup step has completed. */
    private fun onRetired(cleanupFailure: Throwable?) {
        val cause = synchronized(failureGate) { terminalFailure } ?: return
        if (cleanupFailure != null && cleanupFailure !== cause) cause.addSuppressed(cleanupFailure)
        Handler(Looper.getMainLooper()).post {
            if (failureNotified.compareAndSet(false, true)) {
                runCatching { builder.screenDataListener.onError(cause) }.onFailure {
                    if (it !== cause) cause.addSuppressed(it)
                    LogContext.log.e(TAG, "Projection error callback failed", it)
                }
            }
        }
    }

    private fun registerProjectionCallback(): Boolean {
        synchronized(projectionCallbackLock) {
            if (projectionCallbackRegistered) return false
            builder.mediaProjection!!.registerCallback(
                projectionCallback, Handler(Looper.getMainLooper())
            )
            projectionCallbackRegistered = true
            return true
        }
    }

    enum class EncodeType {
        H264,
        H265
    }

    companion object {
        private const val TAG = "ScrRec"
        private const val CALLBACK_SETUP_TIMEOUT_MS = 2_000L
        private const val CALLBACK_THREAD_JOIN_TIMEOUT_MS = 1_000L
    }
}
