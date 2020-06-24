package com.ho1ho.camera2live

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
import android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.camera2live.base.DataProcessContext
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.camera2live.codec.CameraEncoder
import com.ho1ho.camera2live.extensions.fail
import com.ho1ho.camera2live.listeners.CallbackListener
import com.ho1ho.camera2live.view.CameraSurfaceView
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 20-3-25 上午11:22
 */
@Suppress("unused")
class Camera2Component(private val context: Fragment) {

    // Camera2 API supported the MAX width and height
    private val cameraSupportedMaxPreviewWidth: Int by lazy {
        val screenSize = DeviceUtil.getResolution(context.requireContext())
        max(screenSize.x, screenSize.y)
    }
    private val cameraSupportedMaxPreviewHeight: Int by lazy {
        val screenSize = DeviceUtil.getResolution(context.requireContext())
        min(screenSize.x, screenSize.y)
    }

    private lateinit var builder: Builder

    private constructor(context: Fragment, builder: Builder) : this(context) {
        this.builder = builder
    }

    inner class Builder(desiredVideoWidth: Int, desiredVideoHeight: Int) {
        var desiredVideoWidth = desiredVideoWidth
            private set
        var desiredVideoHeight = desiredVideoHeight
            private set

        var cameraSurfaceView: CameraSurfaceView? = null

        //        var previewInFullscreen: Boolean = false
        var quality: Float = BITRATE_NORMAL
        var cameraFps: Range<Int> = CAMERA_FPS_NORMAL
        var videoFps: Int = VIDEO_FPS_FREQUENCY_HIGH
        var iFrameInterval: Int = CameraEncoder.DEFAULT_KEY_I_FRAME_INTERVAL
        var bitrateMode: Int = CameraEncoder.DEFAULT_BITRATE_MODE

        fun textureView(cameraTextureView: CameraSurfaceView) = apply { this.cameraSurfaceView = cameraTextureView }

        //        fun previewInFullscreen(previewInFullscreen: Boolean) = apply { this.previewInFullscreen = previewInFullscreen }
        fun quality(quality: Float) = apply { this.quality = quality }
        fun cameraFps(cameraFps: Range<Int>) = apply { this.cameraFps = cameraFps }
        fun videoFps(videoFps: Int) = apply { this.videoFps = videoFps }
        fun iFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }
        fun bitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun build() = Camera2Component(context, this)
    }

    private var cameraEncoder: CameraEncoder? = null
    private var encodeListener: EncodeDataUpdateListener? = null
    private var lensSwitchListener: LensSwitchListener? = null
    private var supportFlash = false   // Support flash
    private var torchOn = false        // Flash continuously on

    // The context to process data
    private var dataProcessContext: DataProcessContext =
        DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_NORMAL)!!
    var encoderType = DataProcessFactory.ENCODER_TYPE_NORMAL
        set(value) {
            dataProcessContext = DataProcessFactory.getConcreteObject(value) ?: fail("unsupported encoding type=$value")
        }
    // ============================

    private lateinit var cameraId: String                      // Current using CameraId
    private var lensFacing = LENS_FACING_BACK              // Lens facing. LENS_FACING_FRONT=0, LENS_FACING_BACK=1 = 0

    // The camera manager
    private val cameraManager: CameraManager by lazy {
        val context = context.requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var cameraDevice: CameraDevice? = null             // Current camera device
    private var captureSession: CameraCaptureSession? = null   // The configured capture session for a CameraDevice
    lateinit var selectedSizeFromCamera: Size                  // Get the optimized size from camera supported size
    var previewSize: Size? = null                              // Camera preview size as well as the output video size
    private lateinit var imageReader: ImageReader              // The real-time data from Camera
    private var previewRequestBuilder: CaptureRequest.Builder? = null  // The builder for capture requests
    private var previewRequest: CaptureRequest? = null         // The capture request obtained from mPreviewRequestBuilder
    private var orientationListener: OrientationEventListener? = null // Device orientation listener
    private var cameraThread = HandlerThread("camera-background").apply { start() }    // Camera working thread
    private var cameraHandler = Handler(cameraThread.looper)            // Camera working handler used by mCameraThread
    private val cameraOpenCloseLock = Semaphore(1)               // The semaphore for camera status
    private lateinit var cameraSupportedFpsRanges: Array<Range<Int>>    // Camera supported FPS range

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    // ===== Debug code =======================
    private var outputH264ForDebug = false
    private var outputYuvForDebug = false
    private var videoYuvOsForDebug: BufferedOutputStream? = null
    private var videoH264OsForDebug: BufferedOutputStream? = null
    private var baseOutputFolderForDebug: String? = null
    // =========================================

    // Camera status callback
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            CLog.w(TAG, "stateCallback onOpened")
            cameraOpenCloseLock.release()                       // 1. Release a permit in semaphore
            this@Camera2Component.cameraDevice = cameraDevice   // 2. Current opening camera
            createCameraPreviewSession()                        // 3. Create camera preview session
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            CLog.w(TAG, "stateCallback onDisconnected")
            cameraOpenCloseLock.release()               // 1. Release a permit in semaphore
            cameraDevice.close()                        // 2. Close camera
            this@Camera2Component.cameraDevice = null   // 3. Empty camera device
            context.requireActivity().finish()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            CLog.w(TAG, "stateCallback onError")
            cameraOpenCloseLock.release()               // 1. Release a permit in semaphore
            cameraDevice.close()                        // 2. Close camera
            this@Camera2Component.cameraDevice = null   // 3. Empty camera device
            context.requireActivity().finish()
        }
    }

    private val onImageAvailableForRecordingListener = OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

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
                cameraEncoder?.offerDataIntoQueue(rotatedYuv420Data)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image.close()
            }
        }
    }

    fun initializeCamera(lensFacing: Int) {
        // Config camera output
        configCameraOutput(lensFacing)
        initCameraEncoder(
            previewSize!!.width, previewSize!!.height,
            (previewSize!!.width * previewSize!!.height * builder.quality).toInt(),
            builder.videoFps, builder.iFrameInterval, builder.bitrateMode
        )
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

    fun openCameraAndGetData(lensFacing: Int) {
        CLog.w(TAG, "encodingType=$encoderType")

        this.lensFacing = lensFacing

        // If current CameraTextureView is still available(For instance, you move app to background
        // and back again in a short time or make screen off then on), the onSurfaceTextureAvailable callback
        // will not be called. So we just need to openCamera directly. Otherwise, we need to wait for the
        // SurfaceTexture listener.
        if (builder.cameraSurfaceView != null) {
            CLog.w(TAG, "With CameraTextureView")
            openCamera()
        }

        // Clockwise orientation: 0, 90, 180, 270
        // 0: Normal portrait
        orientationListener = object : OrientationEventListener(
            context.requireContext(),
            SensorManager.SENSOR_DELAY_NORMAL
        ) {
            override fun onOrientationChanged(pOrientation: Int) {
                // If the device is just put on the table, we can not get orientation
//                var orientation = pOrientation
//                if (orientation == ORIENTATION_UNKNOWN) {
//                    return
//                }
//
//                orientation = (orientation + 45) / 90 * 90
//                CLog.v(TAG, "mRotateDegree: $orientation")
            }
        }
        if (orientationListener!!.canDetectOrientation()) {
            orientationListener!!.enable()
        } else {
            orientationListener!!.disable()
        }
    }

    fun closeCameraAndStopRecord() {
        try {
            closeCamera()
            stopCameraThread()
            CLog.i(TAG, "yuv2H264Encoder.release()")
            cameraEncoder?.release()
            orientationListener?.disable()

            cameraEncoder = null
            orientationListener = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Open camera.
     * Attention: You muse request and grant android.permission.CAMERA permission before using it.
     *
     *  1. Acquire camera permission
     *  2. According to the camera characteristics, select camera and config output parameters.
     *  3. Open camera using CameraManager
     */
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            builder.cameraSurfaceView?.setDimension(selectedSizeFromCamera.width, selectedSizeFromCamera.height)

            // Try to acquire camera permit in 2500ms.
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            cameraManager.openCamera(cameraId, stateCallback, cameraHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
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
        checkNotNull(cameraEncoder) { "CameraEncoder can not be null" }.setDataUpdateCallback(object :
            CallbackListener {
            override fun onCallback(h264Data: ByteArray) {
                encodeListener?.onUpdate(h264Data)
                if (outputH264ForDebug) videoH264OsForDebug?.write(h264Data)
            }
        })
    }

    /**
     * Config camera output for preview
     *
     *  1. Get camera list to select camera
     *  2. According to the cameraId, get camera characteristics and configuration map
     *  3. Get camera supported fps, orientation and etc from streamConfigurationMap
     *  4. Check the camera orientation then determine whether to swap it.
     *  5. Set the proper camera size and fps for preview
     *  6. Create ImageReader
     */
    private fun configCameraOutput(lensFacing: Int) {
        try {
            // Iterate all camera list
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing != lensFacing) {
                    continue
                }
                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
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

                // The device is normally in portrait by default.
                // Actually, the camera orientation is just 90 degree anticlockwise.
                var cameraWidth = builder.desiredVideoHeight
                var cameraHeight = builder.desiredVideoWidth
                CLog.w(TAG, "cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

                // Landscape: true. Portrait: false
                if (swapDimension) {
                    cameraWidth = builder.desiredVideoWidth
                    cameraHeight = builder.desiredVideoHeight
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
                previewSize =
                    if (swapDimension) Size(selectedSizeFromCamera.height, selectedSizeFromCamera.width) else {
                        selectedSizeFromCamera
                    }
                CLog.w(TAG, "previewSize width=${previewSize!!.width} height=${previewSize!!.height}")

                // The input camera size must be camera supported size.
                // Otherwise, the video will be distorted.
                // The first and second parameters must be supported camera size NOT preview size.
                // That means width is greater then height by default.
                imageReader = ImageReader.newInstance(
                    selectedSizeFromCamera.width,
                    selectedSizeFromCamera.height,
                    ImageFormat.YUV_420_888 /*ImageFormat.JPEG*/,
                    IMAGE_BUFFER_SIZE
                )
                imageReader.setOnImageAvailableListener(onImageAvailableForRecordingListener, cameraHandler)
                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Create preview session
     *
     *  1. Get surface for output
     *  2. Create CaptureRequest.Builder
     *  3. Create capture session and process in callback
     *  4. Config capture request. Set AF, AE, camera FPS and etc
     *  5. SetRepeatingRequest for continually capturing images
     *
     */
    private fun createCameraPreviewSession() {
        try {
            val surfaceList: MutableList<Surface> = ArrayList()
            surfaceList.add(imageReader.surface)
            // Get the surface for output
            builder.cameraSurfaceView?.holder?.let {
                surfaceList.add(it.surface)
            }

            // Create CaptureRequest
            // https://blog.csdn.net/afei__/article/details/86326991
            // CameraDevice.TEMPLATE_PREVIEW
            // CameraDevice.TEMPLATE_STILL_CAPTURE
            // CameraDevice.TEMPLATE_RECORD
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            for (surface in surfaceList) {
                previewRequestBuilder!!.addTarget(surface)
            }
            cameraDevice!!.createCaptureSession( // createCaptureSession // createConstrainedHighSpeedCaptureSession
                surfaceList,
                object : CameraCaptureSession.StateCallback() {
                    // Create capture session
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // When camera is closed, return directly.
                        if (null == cameraDevice) {
                            return
                        }
                        captureSession = cameraCaptureSession
                        try {
                            // AWB
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);

                            // Auto focus
                            previewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            // Auto exposure. The flash will be open automatically in dark.
                            previewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

//                            if (builder.templateType == TEMPLATE_TYPE_PHOTO) {
//                                val rotation: Int = context.requireActivity().windowManager.defaultDisplay.rotation
//                                mPreviewRequestBuilder!!.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS[rotation])
//                            }

                            // Set camera fps according to mCameraSupportedFpsRanges[mCameraSupportedFpsRanges.length-1]
                            CLog.w(TAG, "Camera FPS=${builder.cameraFps}")
                            previewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, builder.cameraFps)
                            previewRequest = previewRequestBuilder!!.build()
                            // Request endlessly repeating capture of images by this capture session.
                            // With this method, the camera device will continually capture images
                            // using the settings in the provided CaptureRequest,
                            // at the maximum rate possible until the session is torn down or session.stopRepeating() is called
                            captureSession!!.setRepeatingRequest(previewRequest!!, null, cameraHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        Toast.makeText(context.requireActivity(), "createCaptureSession Failed", Toast.LENGTH_SHORT).show()
                    }
                }, cameraHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        CLog.i(TAG, "closeCamera()")
        try {
            cameraOpenCloseLock.acquire()

            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            imageReader.close()

            cameraDevice = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun stopCameraThread() {
        CLog.i(TAG, "stopCameraThread()")
        try {
            cameraHandler.removeCallbacksAndMessages(null)
            cameraThread.quitSafely()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height -
                        rhs.width.toLong() * rhs.height
            )
        }
    }

    interface EncodeDataUpdateListener {
        fun onUpdate(h264Data: ByteArray)
    }

    interface LensSwitchListener {
        fun onSwitch(lensFacing: Int)
    }

    fun setEncodeListener(listener: EncodeDataUpdateListener) {
        encodeListener = listener
    }

    fun setLensSwitchListener(listener: LensSwitchListener) {
        lensSwitchListener = listener
    }

    @Suppress("unchecked")
    fun switchCamera(lensFacing: Int) {
        if (cameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

//        Toast.makeText(mContext, "switchCamera=" + lensFacing, Toast.LENGTH_SHORT).show();
        closeCamera()
        this.lensFacing = lensFacing
        initializeCamera(lensFacing)
        openCamera()
        lensSwitchListener?.onSwitch(lensFacing)
    }

    fun switchCamera() {
        if (cameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

        if (lensFacing == LENS_FACING_BACK) {
            switchToFrontCamera()
        } else {
            switchToBackCamera()
        }
    }

    @Suppress("unchecked")
    fun switchToBackCamera() {
        if (cameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

        switchCamera(LENS_FACING_BACK)
    }

    @Suppress("unchecked")
    fun switchToFrontCamera() {
        if (cameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

        switchCamera(LENS_FACING_FRONT)
    }

    fun switchFlash() {
        try {
            if (LENS_FACING_BACK == lensFacing && supportFlash) {
                if (torchOn) {
                    torchOn = false

                    // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
                    // Otherwise the flash will not be off.
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
                    previewRequest = previewRequestBuilder?.build()
                    captureSession?.setRepeatingRequest(previewRequest!!, null, cameraHandler)

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        mCameraManager.setTorchMode(mCameraId, false);
//                    }
                    CLog.w(TAG, "Flash OFF")
                } else {
                    torchOn = true

                    // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
                    // Otherwise the flash will not be on.
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);
                    previewRequest = previewRequestBuilder?.build()
                    captureSession?.setRepeatingRequest(previewRequest!!, null, cameraHandler)

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        mCameraManager.setTorchMode(mCameraId, true);
//                    }
                    CLog.w(TAG, "Flash ON")
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    @Suppress("unused")
    fun setDebugOutputH264(outputH264: Boolean) {
        outputH264ForDebug = outputH264
    }

    /**
     * Notice that, if you do allow to output YUV. ONLY the YUV file will be outputted.
     * The H264 file will not be created no matter what you set for setDebugOutputH264()
     *
     * @param outputYuv Whether to output YUV
     */
    @Suppress("unused")
    fun setDebugOutputYuv(outputYuv: Boolean) {
        outputYuvForDebug = outputYuv
    }

// ============================================

    companion object {
        private const val TAG = "Camera2Component"

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

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
    }
}