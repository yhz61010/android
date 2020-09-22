package com.ho1ho.camera2live

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.AnimationDrawable
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
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
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.ho1ho.androidbase.exts.computeExifOrientation
import com.ho1ho.androidbase.exts.fail
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.androidbase.utils.file.FileUtil
import com.ho1ho.camera2live.base.DataProcessContext
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.camera2live.codec.CameraAvcEncoder
import com.ho1ho.camera2live.listeners.CallbackListener
import com.ho1ho.camera2live.view.CameraSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 20-6-24 下午5:05
 */
@Suppress("unused")
class Camera2ComponentHelper(private val context: FragmentActivity, private var lensFacing: Int, private val cameraView: View? = null) {
    var enableTakePhotoFeature = true
    var enableRecordFeature = true
    var enableGallery = true

    // FIXME Set this vale properly
    var previewWidth: Int = 0
        private set
    var previewHeight: Int = 0
        private set

    private var supportFlash = false   // Support flash
    private var torchOn = false        // Flash continuously on

    private lateinit var cameraId: String
    private var lensSwitchListener: LensSwitchListener? = null

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /**
     * [CameraCharacteristics] corresponding to the provided Camera ID
     * It is initialized in [initializeParameters]
     */
    lateinit var characteristics: CameraCharacteristics

    /////// Recording - Start ///////////////////////////////////////////////////////////
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
        cameraView?.findViewById<TextView>(R.id.txtRecordTime)?.text = "%02d:%02d:%02d".format(hour, minute, second)
        accumulateRecordTime()
    }
    private lateinit var builder: Builder

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
    private var dataProcessContext: DataProcessContext =
        DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_NORMAL)!!
    var encoderType = DataProcessFactory.ENCODER_TYPE_NORMAL
        set(value) {
            dataProcessContext =
                DataProcessFactory.getConcreteObject(value) ?: fail("unsupported encoding type=$value")
        }

    private lateinit var capturePreviewRequestBuilder: CaptureRequest.Builder

    // Camera2 API supported the MAX width and height
    private val cameraSupportedMaxPreviewWidth: Int by lazy {
        val screenSize = DeviceUtil.getResolution(context)
        max(screenSize.x, screenSize.y)
    }
    private val cameraSupportedMaxPreviewHeight: Int by lazy {
        val screenSize = DeviceUtil.getResolution(context)
        min(screenSize.x, screenSize.y)
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
        bitrateMode: Int
    ) {
        cameraEncoder = CameraAvcEncoder(width, height, bitrate, frameRate, iFrameInterval, bitrateMode)
        cameraEncoder.setDataUpdateCallback(object :
            CallbackListener {
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
                    LLog.d(TAG, "$baseOutputFolderForDebug=$mkdirStatus")
                }
                if (outputYuvForDebug) {
                    val videoYuvFile = File(baseOutputFolderForDebug, "camera.yuv")
                    videoYuvOsForDebug = BufferedOutputStream(FileOutputStream(videoYuvFile))
                }
                if (!outputYuvForDebug && outputH264ForDebug) {
                    val videoH264File = File(baseOutputFolderForDebug, "camera.h264")
                    videoH264OsForDebug = BufferedOutputStream(FileOutputStream(videoH264File))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Debug ONLY
     */
    fun closeDebugOutput() {
        try {
            if (outputYuvForDebug) {
                LLog.i(TAG, "output debug yuv file")
                videoYuvOsForDebug?.flush()
                videoYuvOsForDebug?.close()
            }
            if (outputH264ForDebug) {
                LLog.i(TAG, "output debug h264 file")
                videoH264OsForDebug?.flush()
                videoH264OsForDebug?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeRecordingParameters(desiredVideoWidth: Int, desiredVideoHeight: Int) {
        // Generally, if the device is in portrait(Surface.ROTATION_0),
        // the camera SENSOR_ORIENTATION(90) is just in landscape and vice versa.
        val deviceRotation = context.windowManager.defaultDisplay.rotation
        val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1
        var swapDimension = false
        LLog.w(TAG, "deviceRotation: $deviceRotation")                   // deviceRotation: 0
        LLog.w(TAG, "cameraSensorOrientation: $cameraSensorOrientation") // cameraSensorOrientation: 90
        when (deviceRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
                LLog.w(TAG, "swapDimension set true")
                swapDimension = true
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
                swapDimension = true
            }
            else -> LLog.e(TAG, "Display rotation is invalid: $deviceRotation")
        }

        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val allCameraSupportSize = configMap.getOutputSizes(SurfaceHolder::class.java)
        LLog.w(TAG, "allCameraSupportSize: ${allCameraSupportSize?.contentToString()}")

        // The device is normally in portrait by default.
        // Actually, the camera orientation is just 90 degree anticlockwise.
        var cameraWidth = desiredVideoHeight
        var cameraHeight = desiredVideoWidth
        LLog.w(TAG, "cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

        // Landscape: true. Portrait: false
        if (swapDimension) {
            cameraWidth = desiredVideoWidth
            cameraHeight = desiredVideoHeight
        }
        if (cameraWidth > cameraSupportedMaxPreviewHeight) cameraWidth = cameraSupportedMaxPreviewHeight
        if (cameraHeight > cameraSupportedMaxPreviewWidth) cameraHeight = cameraSupportedMaxPreviewWidth
        LLog.w(TAG, "After adjust cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

        // Calculate ImageReader input preview size from supported size list by camera.
        // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
        // Attention: The returned value is in camera orientation. NOT in device orientation.
        selectedSizeFromCamera = getPreviewOutputSize(Size(cameraWidth, cameraHeight), characteristics, SurfaceHolder::class.java)
        // Take care of the result value. It's in camera orientation.
        LLog.w(TAG, "selectedSizeFromCamera width=${selectedSizeFromCamera.width} height=${selectedSizeFromCamera.height}")
        // Swap the selectedPreviewSizeFromCamera is necessary. So that we can use the proper size for CameraTextureView.
        previewSize = if (swapDimension) Size(selectedSizeFromCamera.height, selectedSizeFromCamera.width) else {
            selectedSizeFromCamera
        }
        LLog.w(TAG, "previewSize width=${previewSize!!.width} height=${previewSize!!.height}")
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
    /////// Recording - End ///////////////////////////////////////////////////////////

    init {
        initializeParameters()
    }

    private fun initializeParameters() {
        cameraId = if (CameraMetadata.LENS_FACING_BACK == lensFacing) "0" else "1"
        characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val isFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        LLog.w(TAG, "isFlashSupported=$isFlashSupported")
        this.supportFlash = isFlashSupported ?: false

        // LEVEL_3(3) > FULL(1) > LIMIT(0) > LEGACY(2)
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        LLog.w(TAG, "hardwareLevel=$hardwareLevel")

        // Get camera supported fps. It will be used to create CaptureRequest
        cameraSupportedFpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
        LLog.w(TAG, "cameraSupportedFpsRanges=${cameraSupportedFpsRanges.contentToString()}")

        val highSpeedVideoFpsRanges = configMap.highSpeedVideoFpsRanges
        LLog.w(TAG, "highSpeedVideoFpsRanges=${highSpeedVideoFpsRanges?.contentToString()}")
        val highSpeedVideoSizes = configMap.highSpeedVideoSizes
        LLog.w(TAG, "highSpeedVideoSizes=${highSpeedVideoSizes?.contentToString()}")
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

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    fun extraInitializeCameraForRecording() {
        initializeRecordingParameters(builder.desiredVideoWidth, builder.desiredVideoHeight)
        /** [previewSize] is initialized in [initializeRecordingParameters] */
        initCameraEncoder(
            previewSize!!.width, previewSize!!.height,
            (previewSize!!.width * previewSize!!.height * builder.quality).toInt(),
            builder.videoFps, builder.iFrameInterval, builder.bitrateMode
        )
    }

    fun setImageReaderForRecording() {
        /** [selectedSizeFromCamera] is initialized in [initializeRecordingParameters] */
        if (::imageReader.isInitialized) imageReader.close()
        imageReader = ImageReader.newInstance(
            selectedSizeFromCamera.width,
            selectedSizeFromCamera.height,
            ImageFormat.YUV_420_888 /*ImageFormat.JPEG*/,
            IMAGE_BUFFER_SIZE
        )
    }

    private fun setImageReaderForPhoto(previewWidth: Int, previewHeight: Int) {
        /** [characteristics] is initialized in [initializeParameters] */
        // Initialize an image reader which will be used to capture still photos
//        val size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
//            .maxBy { it.height * it.width }!!
        imageReader = ImageReader.newInstance(previewWidth, previewHeight, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)
    }

    suspend fun setPreviewRepeatingRequest() {
        LLog.i(TAG, "setPreviewRepeatingRequest()")
//        session.stopRepeating()
//        stopRepeating()
        // There is no need to call session.close() method. Please check its comment
//        if (::session.isInitialized) session.close()
        val targets = mutableListOf(imageReader.surface)
        cameraView?.let { targets.add(it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface) }

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        // Capture request holds references to target surfaces
        capturePreviewRequestBuilder =
            session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                cameraView?.let {
                    // Add the preview surface target
                    addTarget(it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface)
                }
                // Auto focus
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // Auto exposure. The flash will be open automatically in dark.
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                // AWB
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)

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
    fun initializeCamera(previewWidth: Int, previewHeight: Int) = context.lifecycleScope.launch(Dispatchers.Main) {
        LLog.i(TAG, "=====> initializeCamera(${previewWidth}x$previewHeight) <=====")
        this@Camera2ComponentHelper.previewWidth = previewWidth
        this@Camera2ComponentHelper.previewHeight = previewHeight
        initializeParameters()

        camera = runCatching {
            // Open the selected camera
            openCamera(cameraManager, cameraId, cameraHandler)
        }.getOrThrow()

        if (enableTakePhotoFeature) {
            val st = SystemClock.elapsedRealtime()
            setImageReaderForPhoto(previewWidth, previewHeight)
            LLog.d(TAG, "=====> Phase1 cost: ${SystemClock.elapsedRealtime() - st}")
            setPreviewRepeatingRequest()
            LLog.d(TAG, "=====> Phase2 cost: ${SystemClock.elapsedRealtime() - st}")
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                LLog.w(TAG, "Camera $cameraId has been disconnected")
                // FIXME In some cases, call this method will cause crash
//                context.requireActivity().finish()
            }

            override fun onClosed(camera: CameraDevice) {
                LLog.w(TAG, "Camera $cameraId has been closed")
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
                device.close()
                val exc = IllegalAccessException("Active: ${cont.isActive} Camera $cameraId error: ($error) $msg.")
                LLog.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                LLog.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private fun stopRepeating() {
        if (::session.isInitialized) {
            LLog.w(TAG, "stopRepeating()")
            session.stopRepeating()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                session.abortCaptures()
            }
            try {
                Thread.sleep(100)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording() {
        LLog.w(TAG, "stopRecording()")
        if (!::imageReader.isInitialized) fail("initializeCamera must be called first")
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
        LLog.w(TAG, "startRecording()")
        if (!::imageReader.isInitialized) fail("initializeCamera must be called first")
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
                (cameraView.findViewById<View>(R.id.vRedDot).background as AnimationDrawable).start()
            }
        }, 1000)

        accumulateRecordTime()
    }

    private fun setRecordRepeatingRequest() {
        imageReader.setOnImageAvailableListener({ reader ->
//            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val image: Image? = reader.acquireLatestImage()
            if (image == null) {
                LLog.w(TAG, "Recording: image is null")
                return@setOnImageAvailableListener
            }
            cameraHandler.post {
                try {
//                    val width = image.width
//                    val height = image.height
//                    LLog.v(TAG, "Image width=$width height=$height")

                    if (outputYuvForDebug) {
                        return@post
                    }

                    val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1
                    val rotatedYuv420Data = dataProcessContext.doProcess(image, lensFacing, cameraSensorOrientation)
                    cameraEncoder.offerDataIntoQueue(rotatedYuv420Data)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }
        }, cameraHandler)

        val targets = mutableListOf(imageReader.surface)
        cameraView?.let { targets.add(it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface) }
        context.lifecycleScope.launch(Dispatchers.Main) {
            session = createCaptureSession(camera, targets, cameraHandler)
            LLog.v(TAG, "setRepeatingRequestForRecord session.device=${session.device}")
            session.setRepeatingRequest(
                // Capture request holds references to target surfaces
                session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    cameraView?.let {
                        // Add the preview and recording surface targets
                        addTarget(it.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface)
                    }
                    addTarget(imageReader.surface)
                    LLog.w(TAG, "Camera FPS=${builder.cameraFps}")
                    // Sets user requested FPS for all targets
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, builder.cameraFps)
                    // Auto focus
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    // AWB
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)

                }.build(), null, cameraHandler
            )
        }
    }

    private fun accumulateRecordTime() {
        cameraView?.postDelayed(recordTimerRunnable, 1000)
    }

    private fun getJpegOrientation(): Int {
        val deviceRotation = context.windowManager.defaultDisplay.rotation
        val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        val jpegOrientation = (ORIENTATIONS.getValue(deviceRotation) + cameraSensorOrientation + 270) % 360
        LLog.d(TAG, "jpegOrientation=$jpegOrientation")
        return jpegOrientation
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
        val st = SystemClock.elapsedRealtime()
        if (!::imageReader.isInitialized) fail("initializeCamera must be called first")
        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            LLog.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE
        ).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
        }
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                cameraView?.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView)?.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                LLog.d(TAG, "Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                context.lifecycleScope.launch(cont.context) {
                    while (true) {
                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp
                        ) continue
                        LLog.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        val buffer = image.planes[0].buffer
                        val width = image.width
                        val height = image.height
                        val imageBytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                        // DO NOT forget for close Image object
                        image.close()

                        val deviceRotation = context.windowManager.defaultDisplay.rotation
                        val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                        // Compute EXIF orientation metadata
                        // FIXME Maybe you will want to use rotation in someday
                        val rotation = 0
//                        val rotation = (context as BaseCamera2Fragment).relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(cameraSensorOrientation, mirrored)
                        LLog.d(
                            TAG,
                            "rotation=$rotation deviceRotation=$deviceRotation cameraSensorOrientation=$cameraSensorOrientation mirrored=$mirrored"
                        )
                        LLog.d(TAG, "=====> Take photo cost: ${SystemClock.elapsedRealtime() - st}")

                        // Build the result and resume progress
                        cont.resume(
                            CombinedCaptureResult(
                                imageBytes,
                                width,
                                height,
                                result,
                                exifOrientation,
                                mirrored,
                                imageReader.imageFormat
                            )
                        )

                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)
    }

    // ===========================================================
    fun turnOnFlash() {
        if (!::camera.isInitialized || !::session.isInitialized) {
            throw IllegalAccessError("You must initialize camera and session first.")
        }
        if (CameraMetadata.LENS_FACING_FRONT == lensFacing || !supportFlash) {
            LLog.w(TAG, "Do NOT support flash or lens facing is front camera.")
            return
        }
        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise the flash will not be on.
        capturePreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        capturePreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);
        torchOn = try {
            session.setRepeatingRequest(capturePreviewRequestBuilder.build(), null, cameraHandler)
            //                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                        mCameraManager.setTorchMode(mCameraId, true);
            //                    }
            LLog.w(TAG, "Flash ON")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun turnOffFlash() {
        if (!::camera.isInitialized || !::session.isInitialized) {
            throw IllegalAccessError("You must initialize camera and session first.")
        }
        if (!supportFlash) {
            LLog.w(TAG, "Do NOT support flash.")
            return
        }
        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise the flash will not be off.
        capturePreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        capturePreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
        torchOn = try {
            val captureRequest = capturePreviewRequestBuilder.build()
            session.setRepeatingRequest(captureRequest, null, cameraHandler)

            //                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                        mCameraManager.setTorchMode(mCameraId, false);
            //                    }
            LLog.w(TAG, "Flash OFF")
            false
        } catch (e: Exception) {
            false
        }
    }

    fun switchFlash() = if (torchOn) turnOffFlash() else turnOnFlash()

    // ===========================================================
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

    @Suppress("unchecked")
    fun switchToBackCamera() {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }

        switchCamera(CameraMetadata.LENS_FACING_BACK)
    }

    @Suppress("unchecked")
    fun switchToFrontCamera() {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }

        switchCamera(CameraMetadata.LENS_FACING_FRONT)
    }

    @Suppress("unchecked")
    fun switchCamera(lensFacing: Int) {
        if (!::camera.isInitialized) {
            throw IllegalAccessError("You must initialize camera first.")
        }

        closeCamera()
        this.lensFacing = lensFacing
        initializeCamera(previewWidth, previewHeight)
        lensSwitchListener?.onSwitch(lensFacing)
    }

    interface LensSwitchListener {
        fun onSwitch(lensFacing: Int)
    }

    fun setLensSwitchListener(listener: LensSwitchListener) {
        lensSwitchListener = listener
    }
// ===========================================================

    /**
     * **Attention:** This method is Debug ONLY
     *
     * Helper function used to save a [CombinedCaptureResult] into a [File]
     */
    suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {

            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                // FIXME The buffer that is just the JPEG data not the original camera image.
                // So I can not mirror image in the general way like this below:
                //if (result.mirrored) mirrorImage(bytes, result.image.width, result.image.height)
                try {
                    val output = FileUtil.createImageFile(context, "jpg")
                    FileOutputStream(output).use { it.write(result.imageBytes) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    LLog.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
//            ImageFormat.RAW_SENSOR -> {
//                val dngCreator = DngCreator(characteristics, result.metadata)
//                try {
//                    val output = createFile(context.requireContext(), "dng")
//                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
//                    cont.resume(output)
//                } catch (exc: IOException) {
//                    LLog.e(TAG, "Unable to write DNG image to file", exc)
//                    cont.resumeWithException(exc)
//                }
//            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.format}")
                LLog.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    /**
     * Just close camera **NOT** release camera resources so that you can reuse it again.
     * If you do want to release camera, this method should be followed by [stopCameraThread].
     * Or just call the handy method [release] to finish the job.
     */
    fun closeCamera() {
        LLog.i(TAG, "closeCamera() - Start")

        try {
            // There is no need to call session.close() method. Please check its comment
//            if (::session.isInitialized) session.close()
            if (::camera.isInitialized) camera.close()
            if (::imageReader.isInitialized) imageReader.close()

            if (::cameraEncoder.isInitialized) cameraEncoder.stop()
        } catch (e: InterruptedException) {
            LLog.e(TAG, "Interrupted while trying to lock camera closing.", e)
        } finally {
            LLog.i(TAG, "closeCamera() - End")
        }
    }

    /**
     * Once you called this method, you must reinitialized this class again if you want to use again.
     * Most of time, when you don't need camera, just call [closeCamera] method, so that you can reuse it again.
     * This method only should be called when you do want to release camera resources and do not want to use it any more.
     */
    @Suppress("WeakerAccess")
    fun stopCameraThread() {
        try {
            if (::cameraEncoder.isInitialized) cameraEncoder.release()
            cameraHandler.removeCallbacksAndMessages(null)
            cameraThread.quitSafely()
            imageReaderHandler.removeCallbacksAndMessages(null)
            imageReaderThread.quitSafely()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        LLog.i(TAG, "=====> stopCameraThread() being called <=====")
    }

    /** Handy method to release all the camera resources. */
    fun release() {
        closeCamera()
        stopCameraThread()
    }

    private fun mirrorImage(imageBytes: ByteArray, w: Int, h: Int) {
        var temp: Byte
        var a: Int
        var b: Int
        var i = 0
        while (i < h) {
            a = i * w
            b = (i + 1) * w - 1
            while (a < b) {
                temp = imageBytes[a]
                imageBytes[a] = imageBytes[b]
                imageBytes[b] = temp
                a++
                b--
            }
            i++
        }
    }

    companion object {
        private val TAG = Camera2ComponentHelper::class.java.simpleName

        private const val ANIMATION_FAST_MILLIS = 50L

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

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
         * In order to use it for camera preview size and avc encode size, this value will be changed to a appropriate size which area is near desired size.
         */
        val CAMERA_SIZE_FOR_VIDEO_CHAT_NORMAL = Size(360, 640)

        @JvmField
        val CAMERA_FPS_VERY_HIGH = Range(30, 30)    // [30, 30]

        @JvmField
        val CAMERA_FPS_HIGH = Range(24, 24)         // [24, 24]

        @JvmField
        val CAMERA_FPS_NORMAL = Range(20, 20)       // [20, 20]

        @JvmField
        val CAMERA_FPS_LOW = Range(15, 15)          // [15, 15]

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
        val format: Int
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