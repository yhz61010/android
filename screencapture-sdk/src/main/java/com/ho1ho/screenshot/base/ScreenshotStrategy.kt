package com.ho1ho.screenshot.base

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.opengl.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.Window
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.CaptureUtil
import com.ho1ho.androidbase.utils.media.ImageUtil
import com.ho1ho.screenshot.TextureRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **Limitation**
 *
 * You can **ONLY** record the activity window.
 *
 * The known components that can not be recorded are list here:(including but not limited to these components)
 *
 * - Toast
 * - Soft keyboard
 *
 * Author: Michael Leo
 * Date: 20-5-15 下午1:53
 */
class ScreenshotStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    companion object {
        private const val TAG = "ScrShotRec"
    }

    @Volatile
    private var isRecording = false
    private val mvp = getMvp()

    // EGL
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // Surface provided by MediaCodec and used to get data produced by OpenGL
    private var surface: Surface? = null

    // Init OpenGL, once we have initialized context and surface
    private lateinit var renderer: TextureRenderer

    private var frameCount: Long = 0

    @SuppressWarnings("unused")
    var spsPpsBytes: ByteArray? = null
        private set
    var h264Encoder: MediaCodec? = null
        private set
    private lateinit var screenshotThread: HandlerThread
    private lateinit var screenshotHandler: Handler

    private var outputFormat: MediaFormat? = null
    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            val outputBuffer = codec.getOutputBuffer(outputBufferId)
            // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
            // bufferFormat is equivalent to member variable outputFormat
            // outputBuffer is ready to be processed or rendered.
            outputBuffer?.let {
                val encodedBytes = ByteArray(info.size)
                it.get(encodedBytes)

                info.presentationTimeUs = computePresentationTimeUs(++frameCount)

                when (info.flags) {
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                        spsPpsBytes = encodedBytes.copyOf()
                        LLog.w(TAG, "Found SPS/PPS frame: ${spsPpsBytes!!.contentToString()}")
                    }
                    MediaCodec.BUFFER_FLAG_KEY_FRAME -> LLog.i(TAG, "Found Key Frame[" + info.size + "]")
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM -> {
                        // Do nothing
                    }
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> {
                        // Do nothing
                    }
                    else -> {
                        // Do nothing
                    }
                }
                screenshotHandler.post {
                    builder.screenDataListener.onDataUpdate(encodedBytes, info.flags)
                }
            }
            codec.releaseOutputBuffer(outputBufferId, false)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
//            CLog.d(TAG, "onOutputFormatChanged format=${format.toJsonString()}")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LLog.d(TAG, "onError error=${e.message}", e)
        }
    }

    class Builder(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val screenDataListener: ScreenDataListener
    ) {
        // FIXME: Seems does not work. Check bellow setKeyFrameRate
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

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setQuality(quality: Int) = apply { this.quality = quality }
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        fun build(): ScreenshotStrategy {
            LLog.w(TAG, "width=$width height=$height dpi=$dpi fps=$fps sampleSize=$sampleSize")
            return ScreenshotStrategy(this)
        }
    }

    private fun getMvp(): FloatArray {
        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        Matrix.scaleM(mvp, 0, 1f, -1f, 1f)
        return mvp
    }

    private fun encodeImages(bitmap: Bitmap) {
        LLog.d(TAG, "encodeImages")

//        val supportSize = h264Encoder!!.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities.isSizeSupported(
//            bitmap.width,
//            bitmap.height
//        )
//        LLog.e(TAG, "isSupportSize[${bitmap.width}x${bitmap.height}]=$supportSize")

        // Render the bitmap/texture here
//            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        renderer.draw(builder.width, builder.height, bitmap, mvp)

        EGLExt.eglPresentationTimeANDROID(
            eglDisplay, eglSurface,
            computePresentationTimeUs(frameCount) * 1000
        )

        // Feed encoder with next frame produced by OpenGL
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun initEgl() {
        surface = h264Encoder?.createInputSurface()
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("eglDisplay == EGL14.EGL_NO_DISPLAY: ${GLUtils.getEGLErrorString(EGL14.eglGetError())}")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1))
            throw RuntimeException("eglInitialize(): " + GLUtils.getEGLErrorString(EGL14.eglGetError()))

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val nConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, nConfigs, 0)

        var err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS)
            throw RuntimeException(GLUtils.getEGLErrorString(err))

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS)
            throw RuntimeException(GLUtils.getEGLErrorString(err))

        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS)
            throw RuntimeException(GLUtils.getEGLErrorString(err))

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
            throw RuntimeException("eglMakeCurrent(): " + GLUtils.getEGLErrorString(EGL14.eglGetError()))
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }

        surface?.release()
        surface = null

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    override fun onInit() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, builder.width, builder.height)
        with(format) {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, builder.bitrate)
            setInteger(MediaFormat.KEY_BITRATE_MODE, builder.bitrateMode)
            setInteger(MediaFormat.KEY_FRAME_RATE, builder.keyFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, builder.iFrameInterval)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Actually, this key has been used in Android 6.0+. However just been opened as of Android 10.
                @Suppress("unchecked")
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // You must specify KEY_LEVEL on Android 6.0+
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
            }
        }
//        h264Encoder = MediaCodec.createByCodecName("OMX.google.h264.encoder")
        h264Encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            outputFormat = it.outputFormat // option B
            it.setCallback(mediaCodecCallback)
        }

        initEgl()

        renderer = TextureRenderer()

        initHandler()
    }

    override fun onStart() {
        isRecording = true
        h264Encoder?.start()
        // Prepare surface
    }

    fun startRecord(window: Window) {
        CoroutineScope(Dispatchers.IO).launch {
            onInit()
            onStart()
            while (isRecording) {
                CaptureUtil.takeScreenshot(window, Bitmap.Config.RGB_565)?.let {
                    if (builder.sampleSize > 1) {
                        val compressedBitmap = ImageUtil.compressBitmap(it, builder.quality, builder.sampleSize)
                        encodeImages(compressedBitmap)
                        compressedBitmap.recycle()
                    } else {
                        encodeImages(it)
                    }
                    it.recycle()
                }
                delay(16)
            }
        }
    }

    override fun onStop() {
        h264Encoder?.stop()
        isRecording = false
    }

    override fun onRelease() {
        onStop()
        releaseHandler()
        h264Encoder?.release()
        releaseEgl()
    }

    private fun initHandler() {
        screenshotThread = HandlerThread("scr-rec-send").apply { start() }
        screenshotHandler = Handler(screenshotThread.looper)
    }

    private fun releaseHandler() {
        if (::screenshotHandler.isInitialized) screenshotHandler.removeCallbacksAndMessages(null)
        if (::screenshotThread.isInitialized) screenshotThread.quitSafely()
    }

    private fun computePresentationTimeUs(frameIndex: Long): Long = (frameIndex * 1_000_000 / builder.fps).toLong()
}