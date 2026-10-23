package com.leovp.screencapture.screenrecord.base.strategies

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.leovp.bytes.toHexString
import com.leovp.image.createBitmap
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import java.nio.ByteBuffer
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:54
 */
class ScreenRecordMediaCodecStrategy private constructor(private val builder: Builder) :
    ScreenProcessor {

    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null

    var h26xEncoder: MediaCodec? = null
        private set
    private var videoEncoderLoop = AtomicBoolean(false)
    private var videoDataSendThread: HandlerThread? = null
    private var videoDataSendHandler: Handler? = null
    private var codecCallbackThread: HandlerThread? = null
    private val projectionCallback = object : MediaProjection.Callback() {}
    private val projectionCallbackLock = Any()
    private var projectionCallbackRegistered = false

    @Suppress("WeakerAccess")
    var vpsSpsPpsBuf: ByteArray? = null
        private set

    private var outputFormat: MediaFormat? = null

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        private var beginPTS: Long = 0L

        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            //            val inputBuffer = codec.getInputBuffer(inputBufferId)
            //            // fill inputBuffer with valid data
            //
            //            codec.queueInputBuffer(inputBufferId,)
            // LogContext.log.d(TAG, "onInputBufferAvailable inputBufferId=$inputBufferId")
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            outputBufferId: Int,
            info: MediaCodec.BufferInfo
        ) {
            runCatching {
                // LogContext.log.d(TAG, "onOutputBufferAvailable outputBufferId=$outputBufferId")
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
                    val calcPTS = if (beginPTS == 0L) {
                        beginPTS = info.presentationTimeUs
                        0
                    } else {
                        info.presentationTimeUs - beginPTS
                    }
                    //                    LogContext.log.w(
                    // TAG, "ori presentationTimeUs=${currentPTS / 1000 / 1000}
                    // new=${info.presentationTimeUs} " +
                    // "currentTimeMillis=${System.currentTimeMillis()} " +
                    // "elapsedRealtime=${SystemClock.elapsedRealtime()} " +
                    // "currentThreadTimeMillis=${SystemClock.currentThreadTimeMillis()} " +
                    //                                "uptimeMillis=${SystemClock.uptimeMillis()}"
                    //                    )
                    onSendAvcFrame(it, info.flags, info.size, calcPTS)
                }
                codec.releaseOutputBuffer(outputBufferId, false)
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            // LogContext.log.d(TAG, "onOutputFormatChanged format=${format.toJsonString()}")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogContext.log.d(TAG, "onError error=${e.message}", e)
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
    @SuppressLint("InlinedApi")
    override fun onInit() {
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
        var callbackRegisteredDuringInit = false
        try {
            val encoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createCodec().also {
                    h26xEncoder = it
                    it.setCallback(mediaCodecCallback, callbackHandler)
                }
            } else {
                val abandoned = AtomicBoolean(false)
                val created = AtomicReference<MediaCodec?>(null)
                val task = FutureTask {
                    val codec = createCodec()
                    created.set(codec)
                    try {
                        check(!abandoned.get()) { "Codec creation was abandoned" }
                        codec.setCallback(mediaCodecCallback)
                        check(!abandoned.get()) { "Codec creation was abandoned" }
                        codec
                    } catch (error: Throwable) {
                        if (created.compareAndSet(codec, null)) codec.release()
                        throw error
                    }
                }
                check(callbackHandler.post(task)) { "Codec callback thread rejected creation" }
                try {
                    task.get(CALLBACK_SETUP_TIMEOUT_MS, TimeUnit.MILLISECONDS).also {
                        check(created.compareAndSet(it, null)) { "Codec ownership handoff failed" }
                    }
                } catch (error: InterruptedException) {
                    abandoned.set(true)
                    task.cancel(true)
                    created.getAndSet(null)?.release()
                    Thread.currentThread().interrupt()
                    throw error
                } catch (error: Throwable) {
                    abandoned.set(true)
                    task.cancel(true)
                    created.getAndSet(null)?.release()
                    throw (error.cause ?: error)
                }
            }
            h26xEncoder = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            outputFormat = encoder.outputFormat
            inputSurface = encoder.createInputSurface()
            callbackRegisteredDuringInit = registerCallbackAboveAndroid14()
            virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
                "screen-record",
                builder.width,
                builder.height,
                builder.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                inputSurface,
                null,
                null
            )
            initHandler()
        } catch (error: Throwable) {
            rollbackFailedInit(error, callbackRegisteredDuringInit)
            throw error
        }
    }

    override fun onRelease() {
        LogContext.log.i(TAG, "onRelease()")
        // No videoEncoderLoop guard here. Callers stop before releasing, which clears that flag,
        // so guarding on it skipped the releases below and leaked the encoder, input surface, and
        // virtual display for the life of the process. onStop() keeps its own guard, and clearing
        // the resource fields makes a repeated release a no-op instead of a double free.
        var failure = collectCleanupFailure(null) { unregisterProjectionCallback() }
        failure = collectCleanupFailure(failure) { onStop() }
        failure = collectCleanupFailure(failure) { releaseHandler() }
        val display = virtualDisplay
        virtualDisplay = null
        failure = collectCleanupFailure(failure) { display?.release() }
        val surface = inputSurface
        inputSurface = null
        failure = collectCleanupFailure(failure) { surface?.release() }
        val encoder = h26xEncoder
        h26xEncoder = null
        failure = collectCleanupFailure(failure) { encoder?.release() }
        failure = collectCleanupFailure(failure) { releaseCodecCallbackThread() }
        failure?.let { throw it }
    }

    override fun onStop() {
        if (!videoEncoderLoop.getAndSet(false)) {
            return
        }
        LogContext.log.i(TAG, "onStop()")
        var failure = collectCleanupFailure(null) { releaseHandler() }
        failure = collectCleanupFailure(failure) { h26xEncoder?.stop() }
        failure = collectCleanupFailure(failure) { builder.mediaProjection?.stop() }
        failure?.let { throw it }
    }

    override fun onStart() {
        if (videoEncoderLoop.get()) {
            LogContext.log.e(TAG, "Your previous recording is not finished. Stop it automatically.")
            onStop()
        }

        LogContext.log.i(TAG, "onStart()")
        initHandler()
        h26xEncoder?.start() ?: throw Exception("You must initialize Video Encoder.")
        videoEncoderLoop.set(true)
    }

    override fun getVideoSize(): Size = Size(builder.width, builder.height)

    /**
     * This method must be called on main thread.
     */
    override fun changeOrientation() {
        runCatching { virtualDisplay?.release() }.onFailure { it.printStackTrace() }
        virtualDisplay = null
        runCatching { inputSurface?.release() }.onFailure { it.printStackTrace() }
        inputSurface = null
        runCatching { h26xEncoder?.release() }.onFailure { it.printStackTrace() }
        h26xEncoder = null
        releaseCodecCallbackThread()
        //        mediaCodecCallback = null
        runCatching { releaseHandler() }.onFailure { it.printStackTrace() }

        videoEncoderLoop.set(false)
        val oriWidth = builder.width
        builder.width = builder.height
        builder.height = oriWidth
        LogContext.log.w(TAG, "Size after orientation: ${builder.width}x${builder.height}")

        //        mediaCodecCallback = createMediaCodecCallback()
        onInit()
        onStart()
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
    }

    private fun rollbackFailedInit(
        originalError: Throwable,
        callbackRegisteredDuringInit: Boolean,
    ) {
        cleanupAfterInitFailure(originalError) { releaseHandler() }
        val display = virtualDisplay
        virtualDisplay = null
        cleanupAfterInitFailure(originalError) { display?.release() }
        if (callbackRegisteredDuringInit) {
            cleanupAfterInitFailure(originalError) { unregisterProjectionCallback() }
        }
        val surface = inputSurface
        inputSurface = null
        cleanupAfterInitFailure(originalError) { surface?.release() }
        val encoder = h26xEncoder
        h26xEncoder = null
        cleanupAfterInitFailure(originalError) { encoder?.release() }
        cleanupAfterInitFailure(originalError) { releaseCodecCallbackThread() }
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
            builder.screenDataListener.onDataUpdate(
                bytes,
                flags,
                presentationTimeUs
            )
        }
    }

    @SuppressLint("WrongConstant")
    @Synchronized
    override fun takeScreenshot(width: Int?, height: Int?, result: (bitmap: Bitmap) -> Unit) {
        val finalWidth = width ?: builder.width
        val finalHeight = height ?: builder.height
        // TODO PixelFormat.RGBA_8888 is a wrong constant? Using ImageFormat instead.
        val imageReader: ImageReader =
            ImageReader.newInstance(finalWidth, finalHeight, PixelFormat.RGBA_8888, 3)
        registerCallbackAboveAndroid14()
        val virtualDisplayForImageReader: VirtualDisplay? =
            builder.mediaProjection!!.createVirtualDisplay(
                "screen-record",
                finalWidth,
                finalHeight,
                builder.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                imageReader.surface,
                null,
                null
            )
        imageReader.setOnImageAvailableListener({ reader ->
            //            LogContext.log.e(TAG, "takeScreenshotFlag=${takeScreenshotFlag.get()}")
            val image: Image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            runCatching {
                result.invoke(image.createBitmap())

                imageReader.setOnImageAvailableListener(null, videoDataSendHandler)
                runCatching { imageReader.close() }.onFailure { it.printStackTrace() }
                runCatching { virtualDisplayForImageReader?.release() }.onFailure {
                    it.printStackTrace()
                }
            }.also { image.close() }
        }, null)
    }

    private fun registerCallbackAboveAndroid14(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        synchronized(projectionCallbackLock) {
            if (projectionCallbackRegistered) return false
            builder.mediaProjection!!.registerCallback(projectionCallback, null)
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
