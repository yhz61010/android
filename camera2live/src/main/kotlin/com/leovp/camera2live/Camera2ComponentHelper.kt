package com.leovp.camera2live

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.AnimationDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR
import android.media.Image
import android.media.ImageReader
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.Keep
import androidx.annotation.RequiresPermission
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import com.leovp.android.exts.createImageFile
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.media.VideoUtil
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.DataProcessContext
import com.leovp.camera2live.base.DataProcessFactory
import com.leovp.camera2live.codec.CameraAvcEncoder
import com.leovp.camera2live.listeners.CallbackListener
import com.leovp.camera2live.utils.getPreviewOutputSize
import com.leovp.camera2live.view.CameraSurfaceView
import com.leovp.exif.computeExifOrientation
import com.leovp.log.LogContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Author: Michael Leo
 * Date: 20-6-24 下午5:05
 */
@Suppress("unused", "TooManyFunctions")
class Camera2ComponentHelper(
    private val context: FragmentActivity,
    private var lensFacing: Int,
    private val cameraView: View? = null,
) {
    var enableTakePhotoFeature = true
    var enableRecordFeature = true
    var enableGallery = true

    var previewWidth: Int = 0
        private set
    var previewHeight: Int = 0
        private set

    private var supportFlash = false // Support flash
    private var torchOn = false // Flash continuously on

    private lateinit var cameraId: String
    private var lensSwitchListener: LensSwitchListener? = null

    /** Reports asynchronous camera errors (e.g. switch close timeout) to the owner. */
    var cameraErrorListener: CameraErrorListener? = null

    /**
     * Serializes [switchCamera] so the old device's [CameraDevice.StateCallback.onClosed] is
     * awaited before the new device is opened (remediation CAM2-3).
     */
    private val switchMutex = Mutex()

    /** The currently opened camera together with a close signal bound to that exact device. */
    private val openedCamera = AtomicReference<OpenedCamera?>()

    /** The in-flight camera switch, cancelled when a new switch is requested. */
    private var switchJob: Job? = null

    /** Owns camera work and is cancelled when this helper is released with its View. */
    private val cameraScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val singleExecutor = Executors.newSingleThreadExecutor()

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /**
     * [CameraCharacteristics] corresponding to the provided Camera ID
     * It is initialized in [initializeParameters]
     */
    lateinit var characteristics: CameraCharacteristics

    // ///// Recording - Start ///////////////////////////////////////////////////////////
    private var recordDuration: Int = 0
    var isRecording = false
        private set

    @SuppressLint("SetTextI18n")
    private var recordTimerRunnable = Runnable {
        if (!isRecording) return@Runnable
        val duration = recordDuration++
        val second = duration % 60
        val minute = duration / 60 % 60
        val hour = duration / 3600 % 60
        cameraView?.findViewById<TextView>(R.id.txtRecordTime)?.text =
            "%02d:%02d:%02d".format(hour, minute, second)
        accumulateRecordTime()
    }
    private lateinit var builder: Builder

    @Keep
    inner class Builder(desiredVideoWidth: Int, desiredVideoHeight: Int) {
        var desiredVideoWidth = desiredVideoWidth
            private set
        var desiredVideoHeight = desiredVideoHeight
            private set
        var quality: Float = BITRATE_NORMAL
        var cameraFps: Range<Int> = CAMERA_FPS_NORMAL
        var videoFps: Int = VIDEO_FPS_FREQUENCY_HIGH
        var iFrameInterval: Int = CameraAvcEncoder.DEFAULT_KEY_I_FRAME_INTERVAL
        var bitrateMode: Int = CameraAvcEncoder.DEFAULT_BITRATE_MODE
        fun build() {
            builder = this
        }
    }

    // The context to process data
    private var dataProcessContext: DataProcessContext? = null

    private lateinit var capturePreviewRequestBuilder: CaptureRequest.Builder

    // Camera2 API supported the MAX width and height
    private val cameraSupportedMaxPreviewWidth: Int by lazy {
        val screenSize = context.screenAvailableResolution
        max(screenSize.width, screenSize.height)
    }
    private val cameraSupportedMaxPreviewHeight: Int by lazy {
        val screenSize = context.screenAvailableResolution
        min(screenSize.width, screenSize.height)
    }

    /**
     * Get the optimized size from camera supported size
     * It is initialized in [initializeRecordingParameters]
     */
    private lateinit var selectedSizeFromCamera: Size

    /** Camera preview size as well as the output video size */
    private var previewSize: Size? = null

    /**
     * Camera supported FPS range
     * It is initialized in [initializeParameters]
     */
    private lateinit var cameraSupportedFpsRanges: Array<Range<Int>>

    interface EncodeDataUpdateListener {
        fun onUpdate(h264Data: ByteArray)
    }

    private lateinit var cameraEncoder: CameraAvcEncoder
    private var encodeListener: EncodeDataUpdateListener? = null
    fun setEncodeListener(listener: EncodeDataUpdateListener) {
        encodeListener = listener
    }

    private fun initCameraEncoder(
        width: Int,
        height: Int,
        bitrate: Int,
        frameRate: Int,
        iFrameInterval: Int,
        bitrateMode: Int,
    ) {
        cameraEncoder =
            CameraAvcEncoder(width, height, bitrate, frameRate, iFrameInterval, bitrateMode)
        // TODO Do we have a better way to check the specific YUV420 type used by MediaCodec?
        dataProcessContext = if (
            CodecUtil.hasCodecByName(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                "OMX.IMG.TOPAZ.VIDEO.Encoder"
            ) ||
            CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder") ||
            CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC") ||
            CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.oppo.h264.encoder")
        ) {
            LogContext.log.w(TAG, "AVC Encode strategy: YUV420P")
            DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_YUV420P)
        } else {
            LogContext.log.w(TAG, "AVC Encode strategy: YUV420SP")
            DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_YUV420SP)
        }
        // dataProcessContext = if (CodecUtil.getSupportedColorFormat(cameraEncoder.h264Encoder,
        // MediaFormat.MIMETYPE_VIDEO_AVC)
        //                .contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
        //        ) {
        //            LogContext.log.w(TAG, "AVC Encode strategy: YUV420P")
        //            DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_YUV420P)
        //        } else {
        //            LogContext.log.w(TAG, "AVC Encode strategy: YUV420SP")
        //            DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_YUV420SP)
        //        }

        cameraEncoder.setDataUpdateCallback(object : CallbackListener {
            override fun onCallback(h264Data: ByteArray) {
                encodeListener?.onUpdate(h264Data)
                if (outputH264ForDebug) videoH264OsForDebug?.write(h264Data)
            }
        })
    }

    /**
     * Debug ONLY
     */
    fun initDebugOutput() {
        try {
            if (outputH264ForDebug || outputYuvForDebug) {
                baseOutputFolderForDebug =
                    context.getExternalFilesDir(null)!!.absolutePath + File.separator + "leo-media"
                val folder = File(baseOutputFolderForDebug!!)
                if (!folder.exists()) {
                    val mkdirStatus = folder.mkdirs()
                    LogContext.log.d(TAG, "$baseOutputFolderForDebug=$mkdirStatus")
                }
                if (outputYuvForDebug) {
                    val videoYuvFile = File(baseOutputFolderForDebug, "camera.yuv")
                    videoYuvOsForDebug = BufferedOutputStream(FileOutputStream(videoYuvFile))
                    LogContext.log.d(TAG, "videoYuvFile=$videoYuvFile")
                }
                if (!outputYuvForDebug && outputH264ForDebug) {
                    val videoH264File = File(baseOutputFolderForDebug, "camera.h264")
                    videoH264OsForDebug = BufferedOutputStream(FileOutputStream(videoH264File))
                    LogContext.log.d(TAG, "videoH264File=$videoH264File")
                }
            }
        } catch (e: Exception) {
            LogContext.log.e(TAG, "initDebugOutput() error.")
        }
    }

    /**
     * Debug ONLY
     */
    fun closeDebugOutput() {
        try {
            if (outputYuvForDebug) {
                LogContext.log.i(TAG, "output debug yuv file")
                videoYuvOsForDebug?.flush()
                videoYuvOsForDebug?.close()
            }
            if (outputH264ForDebug) {
                LogContext.log.i(TAG, "output debug h264 file")
                videoH264OsForDebug?.flush()
                videoH264OsForDebug?.close()
            }
        } catch (e: Exception) {
            LogContext.log.e(TAG, "closeDebugOutput() error.")
        }
    }

    private fun initializeRecordingParameters(desiredVideoWidth: Int, desiredVideoHeight: Int) {
        // Generally, if the device is in portrait(Surface.ROTATION_0),
        // the camera SENSOR_ORIENTATION(90) is just in landscape and vice versa.
        val deviceRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: -1
        } else {
            @Suppress("DEPRECATION")
            context.windowManager.defaultDisplay.rotation
        }
        val cameraSensorOrientation =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1
        var swapDimension = false
        LogContext.log.w(
            TAG,
            "deviceRotation: $deviceRotation"
        ) // deviceRotation: 0
        LogContext.log.w(
            TAG,
            "cameraSensorOrientation: $cameraSensorOrientation"
        ) // cameraSensorOrientation: 90
        when (deviceRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 ->
                if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
                    LogContext.log.w(TAG, "swapDimension set true")
                    swapDimension = true
                }

            Surface.ROTATION_90, Surface.ROTATION_270 ->
                if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
                    swapDimension = true
                }

            else -> LogContext.log.e(
                TAG,
                "Display rotation is invalid: $deviceRotation"
            )
        }

        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val allCameraSupportSize = configMap.getOutputSizes(SurfaceHolder::class.java)
        LogContext.log.w(TAG, "allCameraSupportSize: ${allCameraSupportSize?.contentToString()}")

        // The device is normally in portrait by default.
        // Actually, the camera orientation is just 90 degree anticlockwise.
        var cameraWidth = desiredVideoHeight
        var cameraHeight = desiredVideoWidth
        LogContext.log.w(TAG, "cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

        // Landscape: true. Portrait: false
        if (swapDimension) {
            cameraWidth = desiredVideoWidth
            cameraHeight = desiredVideoHeight
        }
        if (cameraWidth > cameraSupportedMaxPreviewHeight) {
            cameraWidth = cameraSupportedMaxPreviewHeight
        }
        if (cameraHeight > cameraSupportedMaxPreviewWidth) {
            cameraHeight = cameraSupportedMaxPreviewWidth
        }
        LogContext.log.w(TAG, "After adjust cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

        // Calculate ImageReader input preview size from supported size list by camera.
        // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
        // Attention: The returned value is in camera orientation. NOT in device orientation.
        selectedSizeFromCamera =
            getPreviewOutputSize(
                Size(cameraWidth, cameraHeight),
                characteristics,
                SurfaceHolder::class.java
            )
        // Take care of the result value. It's in camera orientation.
        LogContext.log.w(
            TAG,
            "selectedSizeFromCamera width=${selectedSizeFromCamera.width} " +
                "height=${selectedSizeFromCamera.height}"
        )
        // Swap the selectedPreviewSizeFromCamera is necessary. So that we can use the proper size
        // for CameraTextureView.
        previewSize =
            if (swapDimension) {
                Size(selectedSizeFromCamera.height, selectedSizeFromCamera.width)
            } else {
                selectedSizeFromCamera
            }
        LogContext.log.w(
            TAG,
            "previewSize width=${previewSize!!.width} height=${previewSize!!.height}"
        )
    }

    // ===== Debug code =======================
    @Suppress("WeakerAccess")
    var outputH264ForDebug = false

    /**
     * Notice that, if you do allow to output YUV, **ONLY** the YUV file will be outputted.
     * The H264 file will not be created no matter what you set for [outputH264ForDebug]
     */
    @Suppress("WeakerAccess")
    var outputYuvForDebug = false
    private var videoYuvOsForDebug: BufferedOutputStream? = null
    private var videoH264OsForDebug: BufferedOutputStream? = null
    private var baseOutputFolderForDebug: String? = null
    // =========================================
    // ///// Recording - End ///////////////////////////////////////////////////////////

    init {
        initializeParameters()
    }

    private fun initializeParameters() {
        cameraId = if (CameraMetadata.LENS_FACING_BACK == lensFacing) "0" else "1"
        LogContext.log.w(TAG, "cameraId=$cameraId")
        characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val isFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        LogContext.log.w(TAG, "isFlashSupported=$isFlashSupported")
        this.supportFlash = isFlashSupported ?: false

        // LEVEL_3(3) > FULL(1) > LIMIT(0) > LEGACY(2)
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        LogContext.log.w(TAG, "hardwareLevel=$hardwareLevel")

        // Get camera supported fps. It will be used to create CaptureRequest
        cameraSupportedFpsRanges =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
        LogContext.log.w(
            TAG,
            "cameraSupportedFpsRanges=${cameraSupportedFpsRanges.contentToString()}"
        )

        val highSpeedVideoFpsRanges = configMap.highSpeedVideoFpsRanges
        LogContext.log.w(
            TAG,
            "highSpeedVideoFpsRanges=${highSpeedVideoFpsRanges?.contentToString()}"
        )
        val highSpeedVideoSizes = configMap.highSpeedVideoSizes
        LogContext.log.w(TAG, "highSpeedVideoSizes=${highSpeedVideoSizes?.contentToString()}")
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        /**
         * Overlay on top of the camera preview
         */
        val overlay = cameraView?.findViewById<View>(R.id.overlay)
        Runnable {
            overlay?.let {
                // Flash white animation
                it.background = Color.argb(150, 66, 66, 66).toDrawable()
                // Wait for ANIMATION_FAST_MILLIS
                it.postDelayed({
                    // Remove white flash animation
                    it.background = null
                }, ANIMATION_FAST_MILLIS)
            }
        }
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** The [CameraDevice] that this helper opens */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    fun extraInitializeCameraForRecording(bitrate: Int = -1) {
        initializeRecordingParameters(builder.desiredVideoWidth, builder.desiredVideoHeight)
        val autoBitrate = bitrate <= 0

        /** [previewSize] is initialized in [initializeRecordingParameters] */
        val usedBitrate =
            if (autoBitrate) {
                (previewSize!!.width * previewSize!!.height * builder.quality).toInt()
            } else {
                bitrate
            }
        initCameraEncoder(
            previewSize!!.width,
            previewSize!!.height,
            usedBitrate,
            builder.videoFps,
            builder.iFrameInterval,
            builder.bitrateMode
        )
    }

    fun setImageReaderForRecording() {
        /** [selectedSizeFromCamera] is initialized in [initializeRecordingParameters] */
        if (::imageReader.isInitialized) imageReader.close()
        imageReader = ImageReader.newInstance(
            selectedSizeFromCamera.width,
            selectedSizeFromCamera.height,
            /*ImageFormat.JPEG*/
            ImageFormat.YUV_420_888,
            IMAGE_BUFFER_SIZE
        )
    }

    private fun setImageReaderForPhoto(previewWidth: Int, previewHeight: Int) {
        /** [characteristics] is initialized in [initializeParameters] */
        // Initialize an image reader which will be used to capture still photos
        // val size =
        // characteristics
        //     .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        //     .getOutputSizes(ImageFormat.JPEG)
        //     .maxBy { it.height * it.width }!!
        imageReader =
            ImageReader.newInstance(
                previewWidth,
                previewHeight,
                ImageFormat.JPEG,
                IMAGE_BUFFER_SIZE
            )
    }

    suspend fun setPreviewRepeatingRequest() {
        LogContext.log.i(TAG, "setPreviewRepeatingRequest()")
        //        session.stopRepeating()
        //        stopRepeating()
        // There is no need to call session.close() method. Please check its comment
        //        if (::session.isInitialized) session.close()
        val targets = mutableListOf(imageReader.surface)
        cameraView?.let {
            targets.add(it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface)
        }

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        // Capture request holds references to target surfaces
        capturePreviewRequestBuilder =
            session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                cameraView?.let {
                    // Add the preview surface target
                    addTarget(
                        it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface
                    )
                }
                // Autofocus
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                // Auto exposure. The flash will open automatically in the dark.
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                )
                // AWB
                // set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
                // set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
            }
        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(capturePreviewRequestBuilder.build(), null, cameraHandler)
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun initializeCamera(previewWidth: Int, previewHeight: Int) = cameraScope.launch {
        try {
            initializeCameraAndAwait(previewWidth, previewHeight)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportCameraError("initializeCamera failed", e)
        }
    }

    /**
     * Opens and fully configures the camera, returning only once the device, image reader and
     * preview repeating request are all ready. Reuses the existing initialize logic so callers
     * (e.g. [switchCamera]) can await a completed setup (remediation CAM2-3).
     */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    private suspend fun initializeCameraAndAwait(previewWidth: Int, previewHeight: Int) {
        LogContext.log.i(
            TAG,
            "=====> initializeCamera($cameraId)(${previewWidth}x$previewHeight) <====="
        )
        this@Camera2ComponentHelper.previewWidth = previewWidth
        this@Camera2ComponentHelper.previewHeight = previewHeight
        initializeParameters()

        // Open the selected camera
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        if (enableTakePhotoFeature) {
            val st = SystemClock.elapsedRealtime()
            setImageReaderForPhoto(previewWidth, previewHeight)
            LogContext.log.d(
                TAG,
                "=====> Phase1 cost: ${SystemClock.elapsedRealtime() - st}"
            )
            setPreviewRepeatingRequest()
            LogContext.log.d(
                TAG,
                "=====> Phase2 cost: ${SystemClock.elapsedRealtime() - st}"
            )
        }
    }

    /** Opens the camera and returns the opened device as the result of the suspending coroutine. */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        val closeSignal = CompletableDeferred<Unit>()
        val pendingDevice = AtomicReference<CameraDevice?>()
        cont.invokeOnCancellation {
            pendingDevice.getAndSet(null)?.let { device ->
                clearOpenedCamera(device)
                device.close()
            }
        }
        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if (!cont.isActive) {
                    device.close()
                    return
                }
                pendingDevice.set(device)
                val newCamera = OpenedCamera(device, closeSignal)
                if (!openedCamera.compareAndSet(null, newCamera)) {
                    pendingDevice.compareAndSet(device, null)
                    device.close()
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "Another camera became active while opening $cameraId"
                            )
                        )
                    }
                    return
                }
                if (cont.isActive) {
                    cont.resume(device)
                } else {
                    pendingDevice.getAndSet(null)?.close()
                    clearOpenedCamera(device)
                }
            }

            override fun onDisconnected(device: CameraDevice) {
                LogContext.log.w(TAG, "Camera $cameraId has been disconnected")
                pendingDevice.compareAndSet(device, null)
                clearOpenedCamera(device)
                device.close()
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("Camera $cameraId disconnected during open")
                    )
                }
            }

            override fun onClosed(camera: CameraDevice) {
                LogContext.log.w(TAG, "Camera $cameraId has been closed")
                pendingDevice.compareAndSet(camera, null)
                clearOpenedCamera(camera)
                closeSignal.complete(Unit)
                super.onClosed(camera)
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                pendingDevice.compareAndSet(device, null)
                clearOpenedCamera(device)
                device.close()
                val exc = IllegalAccessException(
                    "Active: ${cont.isActive} Camera $cameraId error: ($error) $msg."
                )
                LogContext.log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }
        try {
            manager.openCamera(cameraId, callback, handler)
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resumeWithException(e)
        } catch (e: CameraAccessException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null,
    ): CameraCaptureSession = suspendCancellableCoroutine { cont ->
        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                // If the coroutine was already cancelled, close the session to avoid a leak.
                if (cont.isActive) cont.resume(session) else session.close()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                LogContext.log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }

        cont.invokeOnCancellation {
            LogContext.log.w(TAG, "createCaptureSession cancelled for camera ${device.id}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val outputs: List<OutputConfiguration> = targets.map { OutputConfiguration(it) }
            val config =
                SessionConfiguration(SESSION_REGULAR, outputs, singleExecutor, stateCallback)
            device.createCaptureSession(config)
        } else {
            // Create a capture session using the predefined targets; this also involves defining
            // the
            // session state callback to be notified of when the session is ready
            @Suppress("DEPRECATION")
            device.createCaptureSession(targets, stateCallback, handler)
        }
    }

    private fun stopRepeating() {
        if (!::session.isInitialized) return
        LogContext.log.w(TAG, "stopRepeating()")
        // Serialize teardown through the Camera API instead of a fixed main-thread sleep so we
        // no longer block the UI thread (remediation CAM2-7).
        runCatching {
            session.stopRepeating()
            session.abortCaptures()
        }.onFailure { LogContext.log.w(TAG, "stopRepeating failed", it) }
    }

    fun stopRecording() {
        LogContext.log.w(TAG, "stopRecording()")
        if (!::imageReader.isInitialized) error("initializeCamera must be called first")
        isRecording = false
        cameraView?.run {
            post {
                findViewById<ViewGroup>(R.id.llRecordTime).visibility = View.GONE
                (findViewById<View>(R.id.vRedDot).background as AnimationDrawable).stop()
                findViewById<View>(R.id.ivShotRecord).visibility = View.VISIBLE
                if (enableTakePhotoFeature) {
                    findViewById<View>(R.id.ivShot).visibility = View.VISIBLE
                } else {
                    findViewById<View>(R.id.ivShot).visibility = View.GONE
                }
                if (enableGallery) {
                    findViewById<View>(R.id.ivAlbum).visibility = View.VISIBLE
                } else {
                    findViewById<View>(R.id.ivAlbum).visibility = View.GONE
                }
                findViewById<View>(R.id.ivRecordStop).visibility = View.GONE
                findViewById<View>(R.id.switchFacing).visibility = View.VISIBLE
            }
            removeCallbacks(recordTimerRunnable)
        }
        recordDuration = 0
        stopRepeating()
        closeCamera()
    }

    fun startRecording() {
        LogContext.log.w(TAG, "startRecording()")
        if (!::imageReader.isInitialized) error("initializeCamera must be called first")
        isRecording = true
        cameraView?.run {
            post {
                findViewById<View>(R.id.ivShotRecord).visibility = View.GONE
                findViewById<View>(R.id.ivShot).visibility = View.GONE
                findViewById<View>(R.id.ivRecordStop).visibility = View.VISIBLE
                findViewById<View>(R.id.switchFacing).visibility = View.GONE
                findViewById<View>(R.id.ivAlbum).visibility = View.GONE
            }
        }
        setRecordRepeatingRequest()
        cameraView?.postDelayed({
            if (isRecording) {
                cameraView.findViewById<ViewGroup>(R.id.llRecordTime).visibility = View.VISIBLE
                (
                    cameraView.findViewById<View>(R.id.vRedDot).background as AnimationDrawable
                    ).start()
            }
        }, 1000)

        accumulateRecordTime()
    }

    private fun setRecordRepeatingRequest() {
        imageReader.setOnImageAvailableListener({ reader ->
            // val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val image: Image? = reader.acquireLatestImage()
            if (image == null) {
                LogContext.log.w(TAG, "Recording: image is null")
                return@setOnImageAvailableListener
            }
            if (BuildConfig.DEBUG) {
                val width = image.width
                val height = image.height
                LogContext.log.v(
                    TAG,
                    "Image format[${image.format}]=${VideoUtil.getImageFormatName(image.format)} " +
                        "width=$width height=$height planes=${image.planes.size}"
                )
                if (image.planes.isNotEmpty()) {
                    for ((i, plane) in image.planes.withIndex()) {
                        LogContext.log.v(
                            TAG,
                            "planes[$i] rowStride=${plane.rowStride} " +
                                "pixelStride=${plane.pixelStride} " +
                                "bufferSize=${plane.buffer.remaining()}"
                        )
                    }
                }
            }
            if (outputYuvForDebug) {
                val i420Data = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_I420)
                val convertedYUVData = i420Data

                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.scaleI420(i420Data, image.width,
                // image.height, image.width / 2,
                // image.height / 2, com.leovp.yuv_sdk.YuvUtil.SCALE_FILTER_NONE)
                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.cropI420(i420Data, image.width,
                // image.height, 400, 400, 100, 100)!!
                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.i420ToNv21(i420Data,
                // image.width, image.height)
                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.i420ToNv12(i420Data,
                // image.width, image.height)

                // val nv21Data = com.leovp.yuv_sdk.YuvUtil.i420ToNv21(i420Data, image.width,
                // image.height)
                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.nv21ToI420(nv21Data,
                // image.width, image.height)

                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.nv21ToNv12(nv21Data,
                // image.width, image.height)

                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.nv12ToI420(nv12, image.width,
                // image.height)

                // val nv12Data = com.leovp.yuv_sdk.YuvUtil.i420ToNv12(i420Data, image.width,
                // image.height)
                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.mirrorNv12(nv12Data,
                // image.width, image.height)

                // val scaledWidth = (image.width shr 1) and 7.inv()
                // val scaledHeight = (image.height shr 1) and 7.inv()
                // LogContext.log.w(message ="scaled width=$scaledWidth x $scaledHeight")
                // val convertedYUVData = com.leovp.yuv_sdk.YuvUtil.scaleNv12(nv12Data, image.width,
                // image.height,
                // scaledWidth, scaledHeight)

                videoYuvOsForDebug?.write(convertedYUVData)
                image.close()
                return@setOnImageAvailableListener
            }
            //            cameraHandler.post {
            runCatching {
                val cameraSensorOrientation =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1
                val rotatedYuv420Data =
                    dataProcessContext!!.doProcess(image, lensFacing, cameraSensorOrientation)
                cameraEncoder.offerDataIntoQueue(rotatedYuv420Data)
            }.onFailure { it.printStackTrace() }.also {
                image.close()
            }
            //            }
        }, cameraHandler)

        val targets = mutableListOf(imageReader.surface)
        cameraView?.let {
            targets.add(it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface)
        }
        cameraScope.launch {
            try {
                session = createCaptureSession(camera, targets, cameraHandler)
                LogContext.log.v(
                    TAG,
                    "setRepeatingRequestForRecord session.device=${session.device}"
                )
                session.setRepeatingRequest(
                    // Capture request holds references to target surfaces
                    session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        cameraView?.let {
                            // Add the preview and recording surface targets
                            addTarget(
                                it.findViewById<CameraSurfaceView>(
                                    R.id.cameraSurfaceView
                                ).holder.surface
                            )
                        }
                        addTarget(imageReader.surface)
                        LogContext.log.w(TAG, "Camera FPS=${builder.cameraFps}")
                        // Sets user requested FPS for all targets
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, builder.cameraFps)
                        // Autofocus
                        set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        )
                        // AWB
                        // set(CaptureRequest.CONTROL_AWB_MODE,
                        // CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
                        // set(CaptureRequest.CONTROL_AWB_MODE,
                        // CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
                    }.build(),
                    null,
                    cameraHandler
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportCameraError("Unable to start recording session", e)
            }
        }
    }

    private fun accumulateRecordTime() {
        cameraView?.postDelayed(recordTimerRunnable, 1000)
    }

    private fun getJpegOrientation(): Int {
        val deviceRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: -1
        } else {
            @Suppress("DEPRECATION")
            context.windowManager.defaultDisplay.rotation
        }
        val cameraSensorOrientation =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        val jpegOrientation =
            (ORIENTATIONS.getValue(deviceRotation) + cameraSensorOrientation + 270) % 360
        LogContext.log.d(TAG, "deviceRotation=$deviceRotation jpegOrientation=$jpegOrientation")
        return jpegOrientation
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    suspend fun takePhoto(): CombinedCaptureResult = coroutineScope {
        val st = SystemClock.elapsedRealtime()
        if (!::imageReader.isInitialized) error("initializeCamera must be called first")

        // Flush (and close) any images left in the image reader.
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage()?.also { it.close() } != null) {
        }

        // Bounded channel: drops (and closes) the oldest image on overflow, and closes any image
        // left undelivered when the channel is cancelled (remediation CAM2-1).
        val imageChannel = Channel<Image>(
            capacity = IMAGE_BUFFER_SIZE,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { it.close() }
        )
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
            if (imageChannel.trySend(image).isFailure) image.close()
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE
        ).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
        }

        try {
            withTimeout(IMAGE_CAPTURE_TIMEOUT_MILLIS) {
                val result = suspendCancellableCoroutine { cont ->
                    session.capture(
                        captureRequest.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureStarted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                timestamp: Long,
                                frameNumber: Long,
                            ) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber)
                                cameraView?.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView)
                                    ?.post(animationTask)
                            }

                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult,
                            ) {
                                super.onCaptureCompleted(session, request, result)
                                if (cont.isActive) cont.resume(result)
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure,
                            ) {
                                super.onCaptureFailed(session, request, failure)
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        IllegalStateException(
                                            "Still capture failed: reason=${failure.reason}"
                                        )
                                    )
                                }
                            }
                        },
                        cameraHandler
                    )
                }
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                val combined = dequeueMatchingImage(imageChannel, resultTimestamp, result)
                LogContext.log.d(
                    TAG,
                    "=====> Take photo cost: ${SystemClock.elapsedRealtime() - st}"
                )
                combined
            }
        } finally {
            imageReader.setOnImageAvailableListener(null, null)
            imageChannel.cancel() // onUndeliveredElement closes every buffered image.
        }
    }

    /**
     * Dequeues images from [imageChannel] until one whose timestamp matches [resultTimestamp] is
     * found, closing every non-matching image. Each image is wrapped in [use] so it is always
     * closed even if building the result throws (remediation CAM2-1).
     */
    private suspend fun dequeueMatchingImage(
        imageChannel: Channel<Image>,
        resultTimestamp: Long?,
        result: TotalCaptureResult,
    ): CombinedCaptureResult {
        while (true) {
            val combined = imageChannel.receive().use { image ->
                val mismatched = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    image.format != ImageFormat.DEPTH_JPEG &&
                    image.timestamp != resultTimestamp
                if (mismatched) null else buildCombinedCaptureResult(image, result)
            }
            if (combined != null) return combined
        }
    }

    /** Copies the image bytes and computes EXIF metadata for a single captured [image]. */
    private fun buildCombinedCaptureResult(
        image: Image,
        result: TotalCaptureResult,
    ): CombinedCaptureResult {
        val buffer = image.planes[0].buffer
        val imageBytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
        val cameraSensorOrientation =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_FRONT
        val exifOrientation = computeExifOrientation(cameraSensorOrientation, mirrored)
        return CombinedCaptureResult(
            imageBytes,
            image.width,
            image.height,
            result,
            exifOrientation,
            mirrored,
            imageReader.imageFormat
        )
    }

    // ===========================================================
    fun turnOnFlash() {
        if (!::camera.isInitialized || !::session.isInitialized) {
            throw IllegalAccessError("You must initialize camera and session first.")
        }
        if (CameraMetadata.LENS_FACING_FRONT == lensFacing || !supportFlash) {
            LogContext.log.w(TAG, "Do NOT support flash or lens facing is front camera.")
            return
        }
        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise, the flash will not be on.
        capturePreviewRequestBuilder.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
        capturePreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        // mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
        // CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);
        torchOn = runCatching {
            session.setRepeatingRequest(capturePreviewRequestBuilder.build(), null, cameraHandler)
            //                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                        mCameraManager.setTorchMode(mCameraId, true);
            //                    }
            LogContext.log.w(TAG, "Flash ON")
            true
        }.getOrElse { false }
    }

    fun turnOffFlash() {
        if (!::camera.isInitialized || !::session.isInitialized) {
            throw IllegalAccessError("You must initialize camera and session first.")
        }
        if (!supportFlash) {
            LogContext.log.w(TAG, "Do NOT support flash.")
            return
        }
        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise, the flash will not be off.
        capturePreviewRequestBuilder.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
        capturePreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        // mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
        // CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
        torchOn = runCatching {
            val captureRequest = capturePreviewRequestBuilder.build()
            session.setRepeatingRequest(captureRequest, null, cameraHandler)

            //                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                        mCameraManager.setTorchMode(mCameraId, false);
            //                    }
            LogContext.log.w(TAG, "Flash OFF")
            false
        }.getOrDefault(false)
    }

    fun switchFlash() = if (torchOn) turnOffFlash() else turnOnFlash()

    // ===========================================================
    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun switchCamera() {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }
        if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            switchToFrontCamera()
        } else {
            switchToBackCamera()
        }
    }

    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun switchToBackCamera() {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }

        switchCamera(CameraMetadata.LENS_FACING_BACK)
    }

    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun switchToFrontCamera() {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }

        switchCamera(CameraMetadata.LENS_FACING_FRONT)
    }

    /**
     * Switches to the camera with the given [lensFacing].
     *
     * Remediation CAM2-3: this is now asynchronous. The old device is closed and its
     * [CameraDevice.StateCallback.onClosed] is awaited (serialized through [switchMutex]) before
     * the new device is opened. Consecutive requests cancel the previous not-yet-started switch.
     * On close timeout the switch is aborted and [cameraErrorListener] is notified;
     * [lensSwitchListener] fires only after a successful switch.
     */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun switchCamera(lensFacing: Int) {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }
        LogContext.log.w(TAG, "switchCamera to $lensFacing")

        switchJob?.cancel()
        switchJob = cameraScope.launch {
            try {
                switchMutex.withLock {
                    val oldCamera = checkNotNull(openedCamera.get()) {
                        "No opened camera to switch from."
                    }
                    closeCamera()
                    val closedInTime = withTimeoutOrNull(CAMERA_CLOSE_TIMEOUT_MILLIS) {
                        oldCamera.closed.await()
                        true
                    } ?: false
                    if (!closedInTime) {
                        reportCameraError(
                            "switchCamera failed",
                            TimeoutException("Camera close timed out during switch")
                        )
                        return@withLock
                    }
                    this@Camera2ComponentHelper.lensFacing = lensFacing
                    initializeCameraAndAwait(previewWidth, previewHeight)
                    lensSwitchListener?.onSwitch(lensFacing)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportCameraError("switchCamera failed", e)
            }
        }
    }

    private fun reportCameraError(message: String, error: Throwable) {
        LogContext.log.e(TAG, message, error)
        cameraErrorListener?.onError(error)
    }

    /** Clears only the matching device so a late callback cannot erase a newer camera state. */
    private fun clearOpenedCamera(device: CameraDevice) {
        while (true) {
            val current = openedCamera.get() ?: return
            if (current.device !== device) return
            if (openedCamera.compareAndSet(current, null)) return
        }
    }

    interface LensSwitchListener {
        fun onSwitch(lensFacing: Int)
    }

    fun setLensSwitchListener(listener: LensSwitchListener) {
        lensSwitchListener = listener
    }

    /** The currently opened device with a close signal bound to that exact device (CAM2-3). */
    private data class OpenedCamera(
        val device: CameraDevice,
        val closed: CompletableDeferred<Unit>,
    )

    /** Reports asynchronous camera errors raised outside a suspending call. */
    interface CameraErrorListener {
        fun onError(t: Throwable)
    }
    // ===========================================================

    /**
     * **Attention:** This method is Debug ONLY
     *
     * Helper function used to save a [CombinedCaptureResult] into a [File]
     */
    suspend fun saveResult(result: CombinedCaptureResult): File = when (result.format) {
        // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
        ImageFormat.JPEG, ImageFormat.DEPTH_JPEG ->
            withContext(Dispatchers.IO) {
                // TODO The buffer that is just the JPEG data not the original camera image.
                // So I can not mirror image in the general way like this below:
                // if (result.mirrored) mirrorImage(bytes, result.image.width,
                // result.image.height)
                try {
                    val output = context.createImageFile("jpg")
                    FileOutputStream(output).use { it.write(result.imageBytes) }
                    output
                } catch (exc: IOException) {
                    LogContext.log.e(TAG, "Unable to write JPEG image to file", exc)
                    throw exc
                }
            }

        // When the format is RAW we use the DngCreator utility library
        //            ImageFormat.RAW_SENSOR -> {
        //                val dngCreator = DngCreator(characteristics, result.metadata)
        //                try {
        //                    val output = createFile(context.requireContext(), "dng")
        // FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
        //                    cont.resume(output)
        //                } catch (exc: IOException) {
        //                    LogContext.log.e(TAG, "Unable to write DNG image to file", exc)
        //                    cont.resumeWithException(exc)
        //                }
        //            }

        // No other formats are supported by this sample
        else -> {
            val exc = RuntimeException("Unknown image format: ${result.format}")
            LogContext.log.e(TAG, exc.message, exc)
            throw exc
        }
    }

    /**
     * Just close camera **NOT** release camera resources so that you can reuse it again.
     * If you do want to release camera, this method should be followed by [stopCameraThread].
     * Or just call the handy method [release] to finish the job.
     */
    fun closeCamera() {
        LogContext.log.i(TAG, "closeCamera() - Start")

        try {
            // There is no need to call session.close() method. Please check its comment
            //            if (::session.isInitialized) session.close()
            if (::camera.isInitialized) {
                val device = camera
                device.close()
                clearOpenedCamera(device)
            }
            if (::imageReader.isInitialized) imageReader.close()

            if (::cameraEncoder.isInitialized) cameraEncoder.stop()
        } catch (e: InterruptedException) {
            LogContext.log.e(TAG, "Interrupted while trying to lock camera closing.", e)
        } finally {
            LogContext.log.i(TAG, "closeCamera() - End")
        }
    }

    /**
     * Once you call this method, you must reinitialize this class if you want to use it again.
     * Most of the time, when you don't need the camera, just call [closeCamera], so that you can
     * reuse it later.
     * This method should only be called when you want to release camera resources and do not
     * want to use them anymore.
     */
    @Suppress("WeakerAccess")
    fun stopCameraThread() {
        try {
            if (::cameraEncoder.isInitialized) cameraEncoder.release()
            cameraHandler.removeCallbacksAndMessages(null)
            cameraThread.quitSafely()
            imageReaderHandler.removeCallbacksAndMessages(null)
            imageReaderThread.quitSafely()
            // Release the session-configuration executor so the thread does not leak when the
            // view is destroyed (remediation CAM2-6). shutdown() is idempotent.
            if (!singleExecutor.isShutdown) singleExecutor.shutdown()
        } catch (e: Exception) {
            LogContext.log.e(TAG, "stopCameraThread() error.")
        }
        LogContext.log.i(TAG, "=====> stopCameraThread() being called <=====")
    }

    /** Handy method to release all the camera resources. */
    fun release() {
        switchJob?.cancel()
        switchJob = null
        cameraScope.cancel()
        closeCamera()
        stopCameraThread()
    }

    @Keep
    companion object {
        private val TAG = Camera2ComponentHelper::class.java.simpleName

        private const val ANIMATION_FAST_MILLIS = 50L

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Maximum time to wait for the old camera device to close during a lens switch. */
        private const val CAMERA_CLOSE_TIMEOUT_MILLIS: Long = 3000

        // ===== Camera Recording - Start ============================================
        val ORIENTATIONS = mapOf(
            Surface.ROTATION_0 to 90,
            Surface.ROTATION_90 to 0,
            Surface.ROTATION_180 to 270,
            Surface.ROTATION_270 to 180
        )

        const val TEMPLATE_TYPE_RECORD = 1
        const val TEMPLATE_TYPE_PHOTO = 2

        const val BITRATE_INSANE_HIGH = 2f
        const val BITRATE_EXTREME_HIGH = 1.5f
        const val BITRATE_VERY_HIGH = 1f
        const val BITRATE_HIGH = 0.8f
        const val BITRATE_NORMAL = 0.6f
        const val BITRATE_LOW = 0.4f
        const val BITRATE_VERY_LOW = 0.2f

        val CAMERA_SIZE_EXTRA = Size(1080, 1920)
        val CAMERA_SIZE_HIGH = Size(720, 1280)
        val CAMERA_SIZE_NORMAL = Size(720, 960)
        val CAMERA_SIZE_LOW = Size(480, 640)

        /**
         * This is the desired size.
         * In order to use it for camera preview size and avc encode size,
         * this value will be changed to an appropriate size whose area is near the desired size.
         */
        val CAMERA_SIZE_FOR_VIDEO_CHAT_NORMAL = Size(360, 640)

        @JvmField
        val CAMERA_FPS_VERY_HIGH = Range(30, 30) // [30, 30]

        @JvmField
        val CAMERA_FPS_HIGH = Range(24, 24) // [24, 24]

        @JvmField
        val CAMERA_FPS_NORMAL = Range(20, 20) // [20, 20]

        @JvmField
        val CAMERA_FPS_LOW = Range(15, 15) // [15, 15]

        const val VIDEO_FPS_VERY_HIGH = 25
        const val VIDEO_FPS_FREQUENCY_HIGH = 20
        const val VIDEO_FPS_FREQUENCY_NORMAL = 15
        const val VIDEO_FPS_FREQUENCY_LOW = 10
        const val VIDEO_FPS_FREQUENCY_VERY_LOW = 5
        // ===== Camera Recording - End ============================================
    }

    /** Helper data class used to hold capture metadata with their associated image */
    @Keep
    data class CombinedCaptureResult(
        val imageBytes: ByteArray,
        val width: Int,
        val height: Int,
        val metadata: CaptureResult?,
        val orientation: Int,
        val mirrored: Boolean,
        val format: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CombinedCaptureResult

            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (metadata != other.metadata) return false
            if (orientation != other.orientation) return false
            if (mirrored != other.mirrored) return false
            if (format != other.format) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imageBytes.contentHashCode()
            result = 31 * result + metadata.hashCode()
            result = 31 * result + orientation
            result = 31 * result + mirrored.hashCode()
            result = 31 * result + format
            return result
        }
    }
}
