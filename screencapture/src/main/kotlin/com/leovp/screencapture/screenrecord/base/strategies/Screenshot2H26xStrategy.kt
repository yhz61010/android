package com.leovp.screencapture.screenrecord.base.strategies

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.leovp.image.compressBitmap
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import com.leovp.screencapture.screenrecord.base.TextureRenderer
import com.leovp.screencapture.screenshot.CaptureUtil
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Signals that teardown was requested while the recorder was still initializing. */
private class ReleaseRequestedException :
    CancellationException("Screenshot recorder was released during init")

/** Outcome of an attempt to allocate the recorder's resources. */
private enum class InitOutcome { INITIALIZED, ALREADY_INITIALIZED, FAILED, RELEASED }

/**
 * Screenshot-based H.26x recording strategy for API 21 and later.
 *
 * Author: Michael Leo
 * Date: 20-5-15 下午1:53
 */
class Screenshot2H26xStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    companion object {
        private const val TAG = "ScrShotRec"

        // EGL_ANDROID_recordable. The public EGLExt field was added in API 26.
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        /** Bounded waits used during teardown, so a wedged pipeline cannot block it forever. */
        private const val ENCODER_EOS_TIMEOUT_MS = 500L
        private const val CALLBACK_THREAD_JOIN_TIMEOUT_MS = 2_000L
        private const val EGL_DISPATCH_TIMEOUT_MS = 5_000L
    }

    @Volatile
    private var isRecording = false
    private val releaseRequested = AtomicBoolean(false)
    private val recordingFailure = AtomicReference<Throwable?>(null)
    private val releaseCompleted = CompletableDeferred<Unit>()

    /**
     * Latches when the callback thread outlived its bounded join and was abandoned while still
     * alive. It can still deliver queued frames to the listener, so the teardown is not clean
     * even though [releaseCompleted] opens: see [releaseAndJoin].
     */
    private val callbackThreadAbandoned = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private val codecCallbackLock = Any()
    private val eglThread = AtomicReference<Thread?>(null)

    /**
     * Owns the EGL context for this recorder's whole life. Every EGL call, and the teardown that
     * destroys them, runs here: a context can only be un-currented on the thread it is current on.
     *
     * The factory captures the holder alone, never this recorder. An idle worker thread keeps the
     * executor and its factory reachable, so a factory holding `this` would pin the recorder, its
     * listener and everything the listener captures for as long as the thread lived.
     */
    private val recordingExecutor = eglThread.let { holder ->
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "screenshot-h26x-egl").also { holder.set(it) }
        }
    }
    private val recordingDispatcher = recordingExecutor.asCoroutineDispatcher()
    private val recordingExceptionHandler = CoroutineExceptionHandler { _, error ->
        LogContext.log.e(TAG, "Unhandled screenshot recording failure", error)
    }
    private val recordingScope = CoroutineScope(
        SupervisorJob() + recordingDispatcher + recordingExceptionHandler
    )

    /** Guarded by [lifecycleLock]. */
    private var recordingJob: Job? = null

    /** Guarded by [lifecycleLock]. Latches on the first [startRecord] call. */
    private var recordingStarted = false

    /** Latches once the encoder has been started, so teardown knows whether to stop it. */
    private val encoderStarted = AtomicBoolean(false)

    /**
     * Latches on the first [startEncoder] attempt. [onStart] and [startRecord] are both public
     * entry points that start the encoder, so the documented `onInit(); onStart(); startRecord()`
     * sequence would otherwise call `MediaCodec.start()` on an already executing codec.
     *
     * Kept apart from [encoderStarted], which must stay false when the start itself threw:
     * teardown may only stop a codec that really entered the executing state.
     */
    private val encoderStartRequested = AtomicBoolean(false)

    /**
     * Guarded by [lifecycleLock]. Latches when [requestRelease] takes over teardown because no
     * recording job was ever registered. Ownership is claimed here, while [releaseCompleted]
     * is completed only once the resources are actually gone.
     */
    private var teardownClaimed = false

    /** Guarded by [lifecycleLock]. Latches on the first initialization attempt. */
    private var initStarted = false

    /**
     * Guarded by [lifecycleLock]. The exception that made the first initialization attempt fail.
     *
     * [initStarted] latches before the resources exist, so without this a failed attempt would
     * be indistinguishable from a successful one and the next caller would be told the recorder
     * was already initialized.
     */
    private var initFailure: Throwable? = null

    /** Guarded by [lifecycleLock]. True while resources are still being created. */
    private var initInProgress = false

    /**
     * Guarded by [lifecycleLock]. Set when a claimed teardown was handed over to the thread
     * running [onInit], because only that thread can release the EGL context it created.
     */
    private var teardownDeferredToInit = false

    private val mvp = getMvp()

    // EGL
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // Surface provided by MediaCodec and used to get data produced by OpenGL
    private var surface: Surface? = null

    // Init OpenGL, once we have initialized context and surface
    private lateinit var renderer: TextureRenderer

    private val frameCount = AtomicLong(0)

    /** Opened by the encoder's end-of-stream output, so teardown can drain before stopping. */
    private val encoderEos = CountDownLatch(1)

    @SuppressWarnings("unused")
    var vpsSpsPpsBytes: ByteArray? = null
        private set
    var h26xEncoder: MediaCodec? = null
        private set
    private lateinit var screenshotThread: HandlerThread

    @Volatile
    private var screenshotHandler: Handler? = null

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            outputBufferId: Int,
            info: MediaCodec.BufferInfo
        ) {
            val failed = synchronized(codecCallbackLock) {
                if (h26xEncoder !== codec) return
                try {
                    deliverOutput(codec, outputBufferId, info)
                    false
                } catch (e: Throwable) {
                    // This runs on MediaCodec's callback thread, where an escaping exception
                    // would kill the process instead of reaching the recording coroutine.
                    LogContext.log.e(TAG, "Output buffer callback failed", e)
                    recordingFailure.compareAndSet(null, e)
                    isRecording = false
                    true
                } finally {
                    runCatching { codec.releaseOutputBuffer(outputBufferId, false) }
                        .onFailure { LogContext.log.e(TAG, "releaseOutputBuffer failed", it) }
                }
            }
            // Release only after returning the buffer and leaving the callback lock. This also
            // covers public onInit()/onStart() callers that never created a recording job.
            if (failed) requestRelease()
        }

        private fun deliverOutput(
            codec: MediaCodec,
            outputBufferId: Int,
            info: MediaCodec.BufferInfo
        ) {
            val outputBuffer = codec.getOutputBuffer(outputBufferId)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderEos.countDown()
            // outputBuffer is ready to be processed or rendered.
            outputBuffer?.let {
                val encodedBytes = ByteArray(info.size)
                it.get(encodedBytes)

                val flags = info.flags
                val presentationTimeUs =
                    computePresentationTimeUs(frameCount.incrementAndGet(), builder.fps)

                if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    vpsSpsPpsBytes = encodedBytes.copyOf()
                }
                screenshotHandler?.post {
                    builder.screenDataListener.onDataUpdate(
                        encodedBytes,
                        flags,
                        presentationTimeUs
                    )
                }
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            // LogContext.log.d(TAG, "onOutputFormatChanged format=${format.toJsonString()}")
            // Subsequent data will conform to the new format.
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            synchronized(codecCallbackLock) {
                if (h26xEncoder !== codec || releaseRequested.get()) return
                LogContext.log.e(TAG, "Encoder failed", e)
                recordingFailure.compareAndSet(null, e)
                isRecording = false
            }
            requestRelease()
        }
    }

    class Builder(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val screenDataListener: ScreenDataListener,
    ) {
        var encodeType: ScreenRecordMediaCodecStrategy.EncodeType =
            ScreenRecordMediaCodecStrategy.EncodeType.H264
            private set

        // FIXME This does not seem to work. Check below setKeyFrameRate
        var fps = 20F
            private set
        var quality = 100
            private set
        var sampleSize = 1
            private set
        var bitrate = width * height
            private set
        var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            private set
        var keyFrameRate = 20
            private set
        var iFrameInterval = 1
            private set

        fun setEncodeType(encodeType: ScreenRecordMediaCodecStrategy.EncodeType) =
            apply { this.encodeType = encodeType }

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setQuality(quality: Int) = apply { this.quality = quality }
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        fun build(): Screenshot2H26xStrategy {
            LogContext.log.w(
                TAG,
                "encodeType=$encodeType width=$width height=$height dpi=$dpi fps=$fps " +
                    "sampleSize=$sampleSize"
            )
            return Screenshot2H26xStrategy(this)
        }
    }

    private fun getMvp(): FloatArray {
        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        Matrix.scaleM(mvp, 0, 1f, -1f, 1f)
        return mvp
    }

    private fun encodeImages(bitmap: Bitmap) {
        //        LogContext.log.d(TAG, "encodeImages")

        // val supportSize =
        // h264Encoder!!.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapab
        // ilities.isSizeSupported(
        //            bitmap.width,
        //            bitmap.height
        //        )
        // LogContext.log.e(TAG, "isSupportSize[${bitmap.width}x${bitmap.height}]=$supportSize")

        // Render the bitmap/texture here
        //            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        renderer.draw(builder.width, builder.height, bitmap, mvp)

        EGLExt.eglPresentationTimeANDROID(
            eglDisplay,
            eglSurface,
            computePresentationTimeUs(frameCount.get(), builder.fps) * 1000
        )

        // Feed encoder with next frame produced by OpenGL
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun initEgl() {
        surface = h26xEncoder?.createInputSurface()
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException(
                "eglDisplay == EGL14.EGL_NO_DISPLAY: " +
                    "${GLUtils.getEGLErrorString(EGL14.eglGetError())}"
            )
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException(
                "eglInitialize(): " + GLUtils.getEGLErrorString(EGL14.eglGetError())
            )
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val nConfigs = IntArray(1)
        val configChosen = EGL14.eglChooseConfig(
            eglDisplay,
            attribList,
            0,
            configs,
            0,
            configs.size,
            nConfigs,
            0
        )
        val chooseConfigError = EGL14.eglGetError()
        if (
            !configChosen ||
            chooseConfigError != EGL14.EGL_SUCCESS ||
            nConfigs[0] <= 0 ||
            configs[0] == null
        ) {
            throw RuntimeException(
                "eglChooseConfig(): success=$configChosen count=${nConfigs[0]} " +
                    "error=${GLUtils.getEGLErrorString(chooseConfigError)}"
            )
        }

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            ctxAttribs,
            0
        )

        var err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS) throw RuntimeException(GLUtils.getEGLErrorString(err))

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            configs[0],
            surface,
            surfaceAttribs,
            0
        )
        err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS) throw RuntimeException(GLUtils.getEGLErrorString(err))

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException(
                "eglMakeCurrent(): " + GLUtils.getEGLErrorString(EGL14.eglGetError())
            )
        }
    }

    private fun releaseEgl() {
        val display = eglDisplay
        val context = eglContext
        val windowSurface = eglSurface
        if (display != null && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (windowSurface != null && windowSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, windowSurface)
            }
            if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }

        surface?.release()
        surface = null

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    /**
     * Creates the encoder, EGL objects and callback thread. The work runs on this recorder's own
     * EGL thread, which owns those resources for their whole life; this call blocks until it
     * finishes.
     *
     * Initializing twice is a no-op: this recorder is one-shot and keeps the first set of
     * resources. [startRecord] initializes on its own, so the two are alternatives, not steps.
     *
     * **Do not call this on the main thread.** This implementation tightens the [ScreenProcessor]
     * default: creating an encoder and an EGL context takes as long as the device needs, so the
     * caller blocks for up to five seconds waiting for the EGL thread to pick the work up, and
     * without a bound once that thread has started it — abandoning a half-built EGL context is
     * not an option. Call it from a background thread and publish the result yourself.
     *
     * @throws IllegalStateException if teardown was already requested or already taken over, if
     * a recording already owns the initialization, or if an earlier attempt failed. A released
     * instance is never initialized again; build a new one.
     * @throws InterruptedException if the waiting caller is interrupted. Its interrupt status is
     * restored and teardown is requested on the EGL thread, including resources still being
     * initialized. Teardown may finish after this call throws.
     *
     * Annotated with [Throws] so the JVM signature declares [InterruptedException]. Without it a
     * Java caller cannot even compile a `catch` for it, yet can still be handed one at runtime.
     */
    @Throws(InterruptedException::class)
    override fun onInit() {
        synchronized(lifecycleLock) {
            // startRecord() initializes on the EGL thread it then occupies for the rest of the
            // session. Dispatching this there would queue behind the recording loop and block
            // the caller until recording ends, which on the documented main-thread caller is an
            // ANR rather than an error.
            check(!recordingStarted) {
                "Screenshot recorder is already recording; startRecord() initializes it"
            }
        }
        try {
            when (allocateResourcesOnce()) {
                InitOutcome.INITIALIZED, InitOutcome.ALREADY_INITIALIZED -> Unit
                InitOutcome.FAILED -> throw initAlreadyFailed()
                InitOutcome.RELEASED ->
                    error("Screenshot recorder was already released and cannot be initialized")
            }
        } catch (error: InterruptedException) {
            // The Future may still be queued or already building native resources. Leave that
            // work on its owner thread: beginInit() rejects a queued request, while endInit()
            // takes over teardown for an in-flight one. Interrupting the owner is unsafe.
            requestRelease()
            Thread.currentThread().interrupt()
            throw error
        }
    }

    /** The terminal error for an instance whose one initialization attempt already failed. */
    private fun initAlreadyFailed(): IllegalStateException = IllegalStateException(
        "Screenshot recorder already failed to initialize",
        synchronized(lifecycleLock) { initFailure }
    )

    /** True while the calling thread is the one that owns this recorder's EGL context. */
    private fun isOnEglThread(): Boolean = Thread.currentThread() === eglThread.get()

    /**
     * Runs [action] on the EGL owner thread, blocking the caller until it finishes.
     *
     * Pinning every EGL call to one thread is what makes teardown able to un-current the context;
     * a context made current on a caller's thread could only be destroyed there.
     */
    private fun <T> runOnEglThread(action: () -> T): T {
        if (isOnEglThread()) return action()
        // Whoever wins this claim decides the request's fate: the EGL thread by running it, the
        // caller by giving up on it. FutureTask.cancel() cannot tell a queued task from a running
        // one, and abandoning an action that already runs would leave what it creates owned by
        // nobody, so the bound below covers the queue wait only.
        val claimed = AtomicBoolean(false)
        val task = FutureTask<T> {
            check(claimed.compareAndSet(false, true)) { "Caller gave up on this EGL request" }
            action()
        }
        try {
            recordingExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            // A concurrent teardown shut the EGL thread down between the caller's release
            // checks and this dispatch. beginInit() would have answered RELEASED anyway, so
            // report the documented lifecycle error instead of a raw executor failure.
            throw releasedBeforeDispatch(e)
        }
        return try {
            // Bounded: the EGL thread may be busy capturing a frame, and that capture itself
            // waits on the main thread, so an unbounded wait here can pin the caller.
            awaitEglTask(task, EGL_DISPATCH_TIMEOUT_MS)
        } catch (e: TimeoutException) {
            if (claimed.compareAndSet(false, true)) {
                task.cancel(false)
                error(
                    "EGL thread did not pick up the request within ${EGL_DISPATCH_TIMEOUT_MS}ms: $e"
                )
            }
            awaitEglTask(task, timeoutMs = null)
        }
    }

    /** The terminal error for EGL work requested after the EGL thread was already shut down. */
    private fun releasedBeforeDispatch(cause: RejectedExecutionException): IllegalStateException =
        IllegalStateException("Screenshot recorder was already released", cause)

    /** Waits for [task], unbounded when [timeoutMs] is null, unwrapping the action's failure. */
    private fun <T> awaitEglTask(task: FutureTask<T>, timeoutMs: Long?): T = try {
        if (timeoutMs == null) task.get() else task.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    /**
     * Allocates the encoder, EGL objects and callback thread exactly once.
     *
     * Serialized on the EGL thread, so a caller that uses the public [onInit] and a recording
     * coroutine that initializes lazily cannot both build a set of resources; the second one
     * observes [InitOutcome.ALREADY_INITIALIZED] instead of orphaning the first.
     */
    private fun allocateResourcesOnce(): InitOutcome {
        synchronized(lifecycleLock) {
            // Answer settled cases here rather than on the EGL thread: dispatching would queue
            // behind the recording loop and block the caller for the whole session only to be
            // told the attempt was already made. A failed attempt also claimed its own teardown,
            // so it must be answered before the released checks or the cause would be lost.
            if (initFailure != null) return InitOutcome.FAILED
            if (releaseRequested.get() || teardownClaimed || releaseCompleted.isCompleted) {
                return InitOutcome.RELEASED
            }
            // Only a finished attempt can be answered here. An attempt still in flight has
            // latched initStarted while its resources do not exist yet, so answering
            // ALREADY_INITIALIZED would break the promise that this call blocks until they do.
            // Falling through queues this caller behind the in-flight attempt on the EGL thread,
            // and beginInit() then answers ALREADY_INITIALIZED with the resources really there.
            if (initStarted && !initInProgress) return InitOutcome.ALREADY_INITIALIZED
        }
        return allocateOnEglThread()
    }

    private fun allocateOnEglThread(): InitOutcome = runOnEglThread {
        val outcome = beginInit()
        if (outcome != InitOutcome.INITIALIZED) return@runOnEglThread outcome
        var failure: Throwable? = null
        try {
            initResources()
        } catch (t: Throwable) {
            failure = t
            // Latch the failure before releasing: initStarted is already set, so every later
            // caller would otherwise be told the recorder is initialized and go on to use
            // resources that do not exist.
            synchronized(lifecycleLock) { initFailure = t }
            // Nobody else can release what a failed init created: a caller that used
            // `build().apply { onInit() }` does not even hold a reference yet.
            runCatching { releaseOwnedResources(stopEncoder = false) }
                .onFailure(t::addSuppressed)
            throw t
        } finally {
            // Two cases hand the rest of the teardown to this thread, the only one that can tear
            // the EGL context down: a release requested while init was running, and a failed init
            // that no recording job will finish. Both must also shut the executor down and open
            // the barrier, or an idle EGL thread would keep this recorder reachable for good.
            if (endInit(failed = failure != null)) {
                runCatching { completeInlineRelease(stopEncoder = encoderStarted.get()) }
                    .onFailure { teardownFailure ->
                        // Never rethrown: a teardown failure must not replace the initialization
                        // failure the caller is about to receive, and throwing out of a finally
                        // block would discard that cause silently.
                        val initCause = failure
                        if (initCause != null) {
                            initCause.addSuppressed(teardownFailure)
                        } else {
                            LogContext.log.e(TAG, "Release after init failed", teardownFailure)
                        }
                    }
            }
        }
        outcome
    }

    /**
     * Enters the initializing state. Refuses to build a recorder whose teardown was already
     * requested, otherwise a teardown that ran first would leave everything created here
     * orphaned; refuses a second initialization, which would overwrite the first set of
     * resources with no way left to release it; and refuses to retry after a failed attempt,
     * because the failure already released whatever it had built.
     */
    private fun beginInit(): InitOutcome = synchronized(lifecycleLock) {
        when {
            initFailure != null -> InitOutcome.FAILED

            releaseRequested.get() || teardownClaimed || releaseCompleted.isCompleted ->
                InitOutcome.RELEASED

            initStarted -> InitOutcome.ALREADY_INITIALIZED

            else -> {
                initStarted = true
                initInProgress = true
                InitOutcome.INITIALIZED
            }
        }
    }

    /**
     * Leaves the initializing state and reports whether this thread now owns the teardown:
     * either [requestRelease] deferred it here, or the attempt [failed] and no recording job
     * exists to finish it. The claim mirrors the one in [requestRelease], so a job registered
     * meanwhile keeps the teardown and reports the failure through its own path.
     */
    private fun endInit(failed: Boolean): Boolean = synchronized(lifecycleLock) {
        initInProgress = false
        val deferred = teardownDeferredToInit
        teardownDeferredToInit = false
        when {
            deferred -> true

            !failed || recordingJob != null || teardownClaimed || releaseCompleted.isCompleted ->
                false

            else -> {
                teardownClaimed = true
                true
            }
        }
    }

    private fun initResources() {
        val format = MediaFormat.createVideoFormat(
            when (builder.encodeType) {
                ScreenRecordMediaCodecStrategy.EncodeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                ScreenRecordMediaCodecStrategy.EncodeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            },
            builder.width,
            builder.height
        )
        with(format) {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, builder.bitrate)
            setInteger(MediaFormat.KEY_BITRATE_MODE, builder.bitrateMode)
            setInteger(MediaFormat.KEY_FRAME_RATE, builder.keyFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, builder.iFrameInterval)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Actually, this key has been used in Android 6.0+ although it just has been opened
                // as of Android 10.
                @Suppress("unchecked", "InlinedApi")
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // You must specify KEY_LEVEL on Android 6.0+
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
            }
        }
        //        h264Encoder = MediaCodec.createByCodecName("OMX.google.h264.encoder")
        val encoder = MediaCodec.createEncoderByType(
            when (builder.encodeType) {
                ScreenRecordMediaCodecStrategy.EncodeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                ScreenRecordMediaCodecStrategy.EncodeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            }
        )
        // Publish ownership immediately so a configure/setCallback failure can release this
        // partially initialized codec through the common failure path.
        h26xEncoder = encoder
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.setCallback(mediaCodecCallback)

        initEgl()

        renderer = TextureRenderer()

        initHandler()
    }

    override fun onStart() {
        check(!releaseRequested.get()) { "Screenshot recorder is stopping" }
        startEncoder()
    }

    private fun startEncoder() {
        val encoder = checkNotNull(h26xEncoder) { "onInit() must run before starting the encoder" }
        // Claim after the encoder is known to exist: a caller that reached here without an
        // initialized recorder must not consume the single start this recorder is allowed.
        if (!encoderStartRequested.compareAndSet(false, true)) return
        isRecording = true
        encoder.start()
        encoderStarted.set(true)
    }

    /** Turns a release that raced the recording coroutine into a cancellation, not a failure. */
    private fun ensureNotReleased() {
        if (releaseRequested.get()) throw ReleaseRequestedException()
    }

    /**
     * Starts the one-shot recording loop. The recorder cannot be restarted after [onStop] or
     * [onRelease]; build a new instance instead.
     *
     * @throws IllegalStateException if this recorder was already started, or if an earlier
     * [onInit] failed: that attempt released what it built and shut the EGL thread down, so there
     * is nothing left to record with.
     */
    fun startRecord(act: Activity) {
        synchronized(lifecycleLock) {
            // Said here, with the cause, rather than by a job that the registration below would
            // reject with a misleading "released before it could start".
            if (initFailure != null) throw initAlreadyFailed()
            check(!recordingStarted) { "Screenshot recorder can only be started once" }
            recordingStarted = true
        }
        // Captured weakly: a coroutine stuck in a native teardown call must not keep the
        // recorded Activity reachable.
        val activityRef = WeakReference(act)
        // A lazy job cancelled before it is dispatched completes without ever entering its body,
        // so the finally below never runs. The completion handler cannot tell that apart from a
        // body that ran and released on its way out unless the body says so itself.
        val bodyEntered = AtomicBoolean(false)
        val job = recordingScope.launch(start = CoroutineStart.LAZY) {
            try {
                bodyEntered.set(true)
                ensureNotReleased()
                when (allocateResourcesOnce()) {
                    InitOutcome.INITIALIZED, InitOutcome.ALREADY_INITIALIZED -> Unit
                    // A previous onInit() failed and released whatever it had built, so this
                    // recorder has nothing to record with. Report the original cause.
                    InitOutcome.FAILED -> throw initAlreadyFailed()
                    InitOutcome.RELEASED -> throw ReleaseRequestedException()
                }
                ensureActive()
                // A release requested while onInit() was running is an intentional teardown, not
                // a failure, so it must cancel instead of reaching screenDataListener.onError().
                ensureNotReleased()
                startEncoder()
                // requestRelease() also cancels this job, so a start that races the release
                // request cannot re-arm isRecording and keep the loop alive.
                while (isRecording && !releaseRequested.get()) {
                    ensureActive()
                    if (activityRef.get() == null) {
                        LogContext.log.w(TAG, "Recorded activity is gone. Stop recording.")
                        break
                    }
                    CaptureUtil.takeScreenshot(activityRef, Bitmap.Config.RGB_565)?.let {
                        if (builder.sampleSize > 1) {
                            val compressedBitmap = it.compressBitmap(
                                builder.quality,
                                builder.sampleSize
                            )
                            encodeImages(compressedBitmap)
                            compressedBitmap.recycle()
                        } else {
                            encodeImages(it)
                        }
                        it.recycle()
                    }
                    delay(32.milliseconds)
                }
            } catch (e: CancellationException) {
                // Deliberately not recorded: reportFailureIfAny() filters cancellation out
                // anyway, and storing it would block a later, real failure from being reported.
                throw e
            } catch (e: Throwable) {
                LogContext.log.e(TAG, "Screenshot recording failed", e)
                recordingFailure.compareAndSet(null, e)
            } finally {
                releaseOwnedResources(stopEncoder = encoderStarted.get())
                reportFailureIfAny()
            }
        }
        // Registering the job and claiming the teardown must be mutually exclusive:
        // requestRelease() may already own the teardown, or a previous job may have finished
        // it, in which case starting the job would dispatch onto a shut-down executor.
        val accepted = synchronized(lifecycleLock) {
            if (releaseCompleted.isCompleted || teardownClaimed) {
                false
            } else {
                recordingJob = job
                // Handling completion here, not in the body's finally, also covers a lazy job
                // that is cancelled before it ever runs.
                job.invokeOnCompletion {
                    // Drop the finished coroutine: its continuation keeps the recording
                    // lambda, and everything that lambda captured, reachable from here.
                    // Clearing the job and settling the teardown must look atomic, or a
                    // concurrent requestRelease() would see "no job yet" and claim a
                    // teardown that has in fact already run.
                    val orphaned = synchronized(lifecycleLock) {
                        recordingJob = null
                        // Registering this job took the teardown away from requestRelease(),
                        // which then only cancelled it. If the body never ran, its finally
                        // never released what an earlier onInit() had already allocated, and
                        // no one else is left to do it.
                        val abandoned = !bodyEntered.get() && initStarted && !teardownClaimed
                        if (abandoned) {
                            teardownClaimed = true
                        } else {
                            releaseCompleted.complete(Unit)
                        }
                        abandoned
                    }
                    // completeInlineRelease() closes the dispatcher and opens the barrier once
                    // the resources are really gone, so neither may be done early here.
                    if (orphaned) dispatchInlineRelease() else recordingDispatcher.close()
                }
                true
            }
        }
        if (!accepted) {
            LogContext.log.w(TAG, "Recorder was released before it could start")
            job.cancel()
            return
        }
        job.start()
    }

    /**
     * Requests teardown. This strategy is one-shot, so unlike the general [ScreenProcessor]
     * contract it cannot be started again afterwards; [onStop] and [onRelease] are equivalent.
     */
    override fun onStop() {
        requestRelease()
    }

    /** Equivalent to [onStop] for this one-shot strategy. */
    override fun onRelease() {
        requestRelease()
    }

    /**
     * Requests release and suspends until the EGL owner thread has released every resource.
     *
     * @return true when teardown finished cleanly. False means the callback thread outlived its
     * bounded join and was abandoned while still alive: the barrier opens so the caller is never
     * stuck, but that thread can still deliver queued frames to the listener. A caller that
     * reuses the listener or the output it writes to must retire this recorder's session instead
     * of treating the release as complete.
     */
    suspend fun releaseAndJoin(): Boolean {
        requestRelease()
        releaseCompleted.await()
        return !callbackThreadAbandoned.get()
    }

    override fun getVideoSize(): Size = Size(builder.width, builder.height)

    /**
     * Ends the input stream and waits, bounded, for the encoder to emit its end-of-stream output.
     *
     * Without this the listener never sees [MediaCodec.BUFFER_FLAG_END_OF_STREAM] and has to
     * infer the end of the stream from the transport closing. Must run before the encoder is
     * detached, otherwise the callback that opens [encoderEos] returns early.
     */
    private fun drainEncoder() {
        val encoder = synchronized(codecCallbackLock) { h26xEncoder } ?: return
        runCatching { encoder.signalEndOfInputStream() }
            .onFailure {
                LogContext.log.w(TAG, "signalEndOfInputStream failed", it)
                return
            }
        val drained = runCatching {
            encoderEos.await(ENCODER_EOS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!drained) {
            LogContext.log.w(TAG, "Encoder did not emit EOS within ${ENCODER_EOS_TIMEOUT_MS}ms")
        }
    }

    private fun initHandler() {
        screenshotThread = HandlerThread("scr-rec-send").apply { start() }
        screenshotHandler = Handler(screenshotThread.looper)
    }

    private fun releaseHandlerAndJoin() {
        if (!::screenshotThread.isInitialized) return
        screenshotHandler = null
        if (Thread.currentThread() === screenshotThread) {
            // Teardown was triggered from inside a listener callback. Joining here would wait
            // for this very thread to die, so drop the backlog and let it unwind instead.
            screenshotThread.quit()
            return
        }
        screenshotThread.quitSafely()
        try {
            screenshotThread.join(CALLBACK_THREAD_JOIN_TIMEOUT_MS)
            if (screenshotThread.isAlive) {
                callbackThreadAbandoned.set(true)
                LogContext.log.e(
                    TAG,
                    "Screenshot callback thread still running after " +
                        "${CALLBACK_THREAD_JOIN_TIMEOUT_MS}ms; abandoning it"
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            if (screenshotThread.isAlive) callbackThreadAbandoned.set(true)
            LogContext.log.w(TAG, "Interrupted while joining screenshot callback thread", e)
        }
    }

    private fun requestRelease() {
        releaseRequested.set(true)
        isRecording = false
        // Decide under the same lock startRecord() uses, so a job that is being registered right
        // now either wins (and owns the teardown) or is rejected before it is ever dispatched.
        // Claiming is deliberately not the same thing as completing releaseCompleted: that
        // barrier releases every releaseAndJoin() caller, so it may only be completed once the
        // resources are really gone.
        var ownsInlineRelease = false
        val activeJob = synchronized(lifecycleLock) {
            val job = recordingJob
            if (job == null && !teardownClaimed && !releaseCompleted.isCompleted) {
                teardownClaimed = true
                // Resources may be under construction on the EGL thread right now.
                // Releasing from here would race it and would miss everything it has not
                // created yet, so hand the teardown to that thread; endInit() runs it.
                if (initInProgress) teardownDeferredToInit = true else ownsInlineRelease = true
            }
            job
        }
        activeJob?.cancel()
        // onInit() can be called through the public ScreenProcessor API without startRecord()
        // ever registering a recording job. No coroutine will run the teardown in that case,
        // so release here instead of leaking the encoder, the EGL objects, the input Surface
        // and the callback thread.
        if (ownsInlineRelease) dispatchInlineRelease()
    }

    /**
     * Runs the teardown that no recording coroutine will run, on the thread that owns the EGL
     * context. Releasing EGL from any other thread cannot un-current the context there, which
     * leaves the display, context and surface alive until that thread dies.
     */
    private fun dispatchInlineRelease() {
        val stopEncoder = encoderStarted.get()
        if (isOnEglThread()) {
            completeInlineRelease(stopEncoder)
            return
        }
        try {
            recordingExecutor.execute { completeInlineRelease(stopEncoder) }
        } catch (e: RejectedExecutionException) {
            // The EGL thread is already shut down, so nothing of it can still be current and
            // there is no owner left to drain through. This can land on the MediaCodec callback
            // thread, which is the main Looper here, so skip the encoder drain and stop: they
            // are the long waits. The callback-thread join stays, bounded, below.
            LogContext.log.e(
                TAG,
                "EGL thread is gone. Releasing on ${Thread.currentThread().name}",
                e
            )
            completeInlineRelease(stopEncoder = false)
        }
    }

    /** Reports a recorded failure to the listener, isolating an exception thrown by it. */
    private fun reportFailureIfAny() {
        val failure = recordingFailure.get()
        if (failure == null || failure is CancellationException) return
        try {
            builder.screenDataListener.onError(failure)
        } catch (callbackFailure: Throwable) {
            LogContext.log.e(TAG, "Screen error callback failed", callbackFailure)
        }
    }

    /** Releases the owned resources and only then opens the [releaseAndJoin] barrier. */
    private fun completeInlineRelease(stopEncoder: Boolean) {
        try {
            releaseOwnedResources(stopEncoder = stopEncoder)
        } finally {
            recordingDispatcher.close()
            releaseCompleted.complete(Unit)
        }
        // No recording coroutine exists on this path, so this is the only place a codec failure
        // can still reach the listener. It runs after the barrier so a listener that calls back
        // into this recorder cannot deadlock against releaseAndJoin().
        reportFailureIfAny()
    }

    /**
     * Releases everything [onInit] created. Must run on the EGL thread, the only one that can
     * un-current the context it created. Every step is null-safe, so a partially or never
     * initialized recorder is handled too.
     */
    private fun releaseOwnedResources(stopEncoder: Boolean) {
        // Acquiring the callback lock waits for an in-flight callback. Detaching under the same
        // lock makes every later callback return before touching the encoder.
        if (stopEncoder) drainEncoder()
        val encoder = synchronized(codecCallbackLock) {
            h26xEncoder.also { h26xEncoder = null }
        }
        if (stopEncoder) {
            try {
                encoder?.stop()
            } catch (error: Throwable) {
                LogContext.log.e(TAG, "Encoder stop failed", error)
            }
        }
        synchronized(codecCallbackLock) {
            try {
                encoder?.release()
            } catch (error: Throwable) {
                LogContext.log.e(TAG, "Encoder release failed", error)
            }
        }
        try {
            releaseEgl()
        } catch (error: Throwable) {
            LogContext.log.e(TAG, "EGL release failed", error)
        } finally {
            releaseHandlerAndJoin()
        }
    }
}
