package com.ho1ho.camera2live

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ho1ho.androidbase.exts.computeExifOrientation
import com.ho1ho.androidbase.exts.fail
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.camera2live.base.DataProcessContext
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.camera2live.codec.CameraEncoder
import com.ho1ho.camera2live.listeners.CallbackListener
import com.ho1ho.camera2live.view.BaseCamera2Fragment
import com.ho1ho.camera2live.view.CameraSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
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
class Camera2ComponentHelper(
    private val context: Fragment, private var lensFacing: Int, private val cameraView: View
) {
    private var inRecordMode = false
    private var supportFlash = false   // Support flash
    private var torchOn = false        // Flash continuously on

    private lateinit var cameraId: String
    private var lensSwitchListener: LensSwitchListener? = null

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = context.requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    lateinit var characteristics: CameraCharacteristics

    // FIXME Recording
    /////// Recording - Start ///////////////////////////////////////////////////////////
    private lateinit var builder: Builder

    inner class Builder(desiredVideoWidth: Int, desiredVideoHeight: Int) {
        var desiredVideoWidth = desiredVideoWidth
            private set
        var desiredVideoHeight = desiredVideoHeight
            private set
        var quality: Float = BITRATE_NORMAL
        var cameraFps: Range<Int> = CAMERA_FPS_NORMAL
        var videoFps: Int = VIDEO_FPS_FREQUENCY_HIGH
        var iFrameInterval: Int = CameraEncoder.DEFAULT_KEY_I_FRAME_INTERVAL
        var bitrateMode: Int = CameraEncoder.DEFAULT_BITRATE_MODE
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
                DataProcessFactory.getConcreteObject(value) ?: com.ho1ho.camera2live.extensions.fail("unsupported encoding type=$value")
        }

    private lateinit var capturePreviewRequestBuilder: CaptureRequest.Builder

    /** Requests used for preview only in the [CameraCaptureSession] */
    private val previewRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        capturePreviewRequestBuilder =
            session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                // Add the preview surface target
                addTarget(cameraView.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface)
                // Auto focus
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // Auto exposure. The flash will be open automatically in dark.
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                // AWB
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)

            }
        capturePreviewRequestBuilder.build()
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(cameraView.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).holder.surface)
            // FIXME
            addTarget(imageReader.surface)
            CLog.w(TAG, "Camera FPS=${builder.cameraFps}")
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, builder.cameraFps)
            // Auto focus
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            // AWB
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
//        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)

        }.build()
    }

    // Camera2 API supported the MAX width and height
    private val cameraSupportedMaxPreviewWidth: Int by lazy {
        val screenSize = DeviceUtil.getResolution(context.requireContext())
        max(screenSize.x, screenSize.y)
    }
    private val cameraSupportedMaxPreviewHeight: Int by lazy {
        val screenSize = DeviceUtil.getResolution(context.requireContext())
        min(screenSize.x, screenSize.y)
    }

    /** Get the optimized size from camera supported size */
    private lateinit var selectedSizeFromCamera: Size

    /** Camera preview size as well as the output video size */
    private var previewSize: Size? = null

    /** Camera supported FPS range */
    private lateinit var cameraSupportedFpsRanges: Array<Range<Int>>

    interface EncodeDataUpdateListener {
        fun onUpdate(h264Data: ByteArray)
    }

    private lateinit var cameraEncoder: CameraEncoder
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
        cameraEncoder = CameraEncoder(width, height, bitrate, frameRate, iFrameInterval, bitrateMode)
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
                    context.requireContext().getExternalFilesDir(null)!!.absolutePath + File.separator + "leo-media"
                val folder = File(baseOutputFolderForDebug!!)
                if (!folder.exists()) {
                    val mkdirStatus = folder.mkdirs()
                    CLog.d(TAG, "$baseOutputFolderForDebug=$mkdirStatus")
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
                CLog.i(TAG, "release videoYuvOs")
                videoYuvOsForDebug?.flush()
                videoYuvOsForDebug?.close()
            }
            if (outputH264ForDebug) {
                CLog.i(TAG, "release videoH264Os")
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
        val deviceRotation = context.requireActivity().windowManager.defaultDisplay.rotation
        val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1
        var swapDimension = false
        CLog.w(TAG, "deviceRotation: $deviceRotation")                   // deviceRotation: 0
        CLog.w(TAG, "cameraSensorOrientation: $cameraSensorOrientation") // cameraSensorOrientation: 90
        when (deviceRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
                CLog.w(TAG, "swapDimension set true")
                swapDimension = true
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
                swapDimension = true
            }
            else -> CLog.e(TAG, "Display rotation is invalid: $deviceRotation")
        }

        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val allCameraSupportSize = configMap.getOutputSizes(SurfaceHolder::class.java)
        CLog.w(TAG, "allCameraSupportSize: ${allCameraSupportSize?.contentToString()}")

        // The device is normally in portrait by default.
        // Actually, the camera orientation is just 90 degree anticlockwise.
        var cameraWidth = desiredVideoHeight
        var cameraHeight = desiredVideoWidth
        CLog.w(TAG, "cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

        // Landscape: true. Portrait: false
        if (swapDimension) {
            cameraWidth = desiredVideoWidth
            cameraHeight = desiredVideoHeight
        }
        if (cameraWidth > cameraSupportedMaxPreviewHeight) cameraWidth = cameraSupportedMaxPreviewHeight
        if (cameraHeight > cameraSupportedMaxPreviewWidth) cameraHeight = cameraSupportedMaxPreviewWidth
        CLog.w(TAG, "After adjust cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

        // Calculate ImageReader input preview size from supported size list by camera.
        // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
        // Attention: The returned value is in camera orientation. NOT in device orientation.
        selectedSizeFromCamera = getPreviewOutputSize(Size(cameraWidth, cameraHeight), characteristics, SurfaceHolder::class.java)
        // Take care of the result value. It's in camera orientation.
        CLog.w(TAG, "selectedSizeFromCamera width=${selectedSizeFromCamera.width} height=${selectedSizeFromCamera.height}")
        // Swap the selectedPreviewSizeFromCamera is necessary. So that we can use the proper size for CameraTextureView.
        previewSize = if (swapDimension) Size(selectedSizeFromCamera.height, selectedSizeFromCamera.width) else {
            selectedSizeFromCamera
        }
        CLog.w(TAG, "previewSize width=${previewSize!!.width} height=${previewSize!!.height}")
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
        CLog.w(TAG, "isFlashSupported=$isFlashSupported")
        this.supportFlash = isFlashSupported ?: false

        // LEVEL_3(3) > FULL(1) > LIMIT(0) > LEGACY(2)
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        CLog.w(TAG, "hardwareLevel=$hardwareLevel")

        // Get camera supported fps. It will be used to create CaptureRequest
        cameraSupportedFpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
        CLog.w(TAG, "cameraSupportedFpsRanges=${cameraSupportedFpsRanges.contentToString()}")

        val highSpeedVideoFpsRanges = configMap.highSpeedVideoFpsRanges
        CLog.w(TAG, "highSpeedVideoFpsRanges=${highSpeedVideoFpsRanges?.contentToString()}")
        val highSpeedVideoSizes = configMap.highSpeedVideoSizes
        CLog.w(TAG, "highSpeedVideoSizes=${highSpeedVideoSizes?.contentToString()}")
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
        val overlay = cameraView.findViewById<View>(R.id.overlay)
        Runnable {
            // Flash white animation
            overlay.background = Color.argb(150, 66, 66, 66).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            overlay.postDelayed({
                // Remove white flash animation
                overlay.background = null
            }, ANIMATION_FAST_MILLIS)
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

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    fun initializeCamera(inRecordMode: Boolean) = context.lifecycleScope.launch(Dispatchers.Main) {
        this@Camera2ComponentHelper.inRecordMode = inRecordMode
        initializeParameters()
        if (inRecordMode) {
            initializeRecordingParameters(builder.desiredVideoWidth, builder.desiredVideoHeight)
            initCameraEncoder(
                previewSize!!.width, previewSize!!.height,
                (previewSize!!.width * previewSize!!.height * builder.quality).toInt(),
                builder.videoFps, builder.iFrameInterval, builder.bitrateMode
            )
        }

        // Open the selected camera
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        imageReader = if (inRecordMode) {
            ImageReader.newInstance(
                selectedSizeFromCamera.width,
                selectedSizeFromCamera.height,
                ImageFormat.YUV_420_888 /*ImageFormat.JPEG*/,
                IMAGE_BUFFER_SIZE
            )
        } else {
            // Initialize an image reader which will be used to capture still photos
            val size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
                .maxBy { it.height * it.width }!!
            ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)
        }
        // FIXME How can I use this?
//        if (inRecordMode) {
//            startRecording()
//        }

        val cameraSurfaceView = cameraView.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(cameraSurfaceView.holder.surface, imageReader.surface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(previewRequest, null, cameraHandler)
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
                Log.w(TAG, "Camera $cameraId has been disconnected")
                context.requireActivity().finish()
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
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
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
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    suspend fun startRecording(): CombinedCaptureResult = suspendCoroutine { /*cont ->*/
        if (!::imageReader.isInitialized) fail("initializeCamera must be called first")

        session.setRepeatingRequest(recordRequest, null, cameraHandler)

        imageReader.setOnImageAvailableListener({ reader ->
//            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val image: Image? = reader.acquireLatestImage()
            if (image == null) {
                CLog.w(TAG, "Recording: image is null")
                return@setOnImageAvailableListener
            }
            cameraHandler.post {
                try {
                    val width = image.width
                    val height = image.height
                    CLog.v(TAG, "Image width=$width height=$height")

                    if (outputYuvForDebug) {
                        videoYuvOsForDebug?.write(dataProcessContext.doProcess(image, lensFacing))
                        return@post
                    }

                    val rotatedYuv420Data = dataProcessContext.doProcess(image, lensFacing)
                    cameraEncoder.offerDataIntoQueue(rotatedYuv420Data)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }
        }, cameraHandler)
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
        if (!::imageReader.isInitialized) fail("initializeCamera must be called first")
        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE
        ).apply { addTarget(imageReader.surface) }
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                cameraView.findViewById<CameraSurfaceView>(R.id.cameraSurfaceView).post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

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
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        val deviceRotation = context.requireActivity().windowManager.defaultDisplay.rotation
                        val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1

                        // Compute EXIF orientation metadata
                        val rotation = (context as BaseCamera2Fragment).relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(cameraSensorOrientation, mirrored)
                        Log.d(
                            TAG,
                            "rotation=$rotation deviceRotation=$deviceRotation cameraSensorOrientation=$cameraSensorOrientation mirrored=$mirrored"
                        )

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(image, result, exifOrientation, imageReader.imageFormat))

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
            Log.w(TAG, "Do NOT support flash or lens facing is front camera.")
            return
        }
        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise the flash will not be on.
        capturePreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        capturePreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);
        torchOn = try {
            val captureRequest = capturePreviewRequestBuilder.build()
            session.setRepeatingRequest(captureRequest, null, cameraHandler)

            //                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                        mCameraManager.setTorchMode(mCameraId, true);
            //                    }
            Log.w(TAG, "Flash ON")
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
            Log.w(TAG, "Do NOT support flash.")
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
            Log.w(TAG, "Flash OFF")
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
        initializeCamera(inRecordMode)
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
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val output = createFile(context.requireContext(), "jpg")
                    FileOutputStream(output).use { it.write(bytes) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val output = createFile(context.requireContext(), "dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
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
        CLog.i(TAG, "closeCamera()")

        try {
            if (::session.isInitialized) session.close()
            if (::camera.isInitialized) camera.close()
            if (::imageReader.isInitialized) imageReader.close()

            if (::cameraEncoder.isInitialized) cameraEncoder.release()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.", e)
        }
    }

    /**
     * Once you called this method, you must reinitialized this class again if you want to use again.
     * Most of time, when you don't need camera, just call [closeCamera] method, so that you can reuse it again.
     * This method only should be called when you do want to release camera resources and do not want to use it any more.
     */
    @Suppress("WeakerAccess")
    fun stopCameraThread() {
        cameraHandler.removeCallbacksAndMessages(null)
        cameraThread.quitSafely()
        imageReaderHandler.removeCallbacksAndMessages(null)
        imageReaderThread.quitSafely()
    }

    /** Handy method to release all the camera resources. */
    fun release() {
        closeCamera()
        stopCameraThread()
    }

    companion object {
        private val TAG = Camera2ComponentHelper::class.java.simpleName

        private const val ANIMATION_FAST_MILLIS = 50L

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        /**
         * **Attention:** This method is Debug ONLY
         *
         * Create a [File] named a using formatted timestamp with the current date and time.
         *
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            return File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_${sdf.format(Date())}.$extension")
        }

        // ===== Camera Recording - Start ============================================
        val ORIENTATIONS = mapOf(
            Surface.ROTATION_0 to 90,
            Surface.ROTATION_90 to 0,
            Surface.ROTATION_180 to 270,
            Surface.ROTATION_270 to 180
        )

        const val TEMPLATE_TYPE_RECORD = 1
        const val TEMPLATE_TYPE_PHOTO = 2

        const val BITRATE_INSANE_HIGH = 8f
        const val BITRATE_EXTREME_HIGH = 5f
        const val BITRATE_VERY_HIGH = 3f
        const val BITRATE_HIGH = 2f
        const val BITRATE_NORMAL = 1f
        const val BITRATE_LOW = 0.75f
        const val BITRATE_VERY_LOW = 0.5f

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

}