package com.ho1ho.camera2live

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
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
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.camera2live.base.DataProcessContext
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.camera2live.codec.CameraEncoder
import com.ho1ho.camera2live.extensions.fail
import com.ho1ho.camera2live.listeners.CallbackListener
import com.ho1ho.camera2live.view.CameraTextureView
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
class Camera2Component private constructor(var builder: Builder) {

    class Builder(ctx: Activity, desiredVideoWidth: Int, desiredVideoHeight: Int) {
        var context = ctx
            private set
        var desiredVideoWidth = desiredVideoWidth
            private set
        var desiredVideoHeight = desiredVideoHeight
            private set

        var cameraTextureView: CameraTextureView? = null
        var previewInFullscreen: Boolean = false
        var quality: Float = BITRATE_NORMAL
        var cameraFps: Range<Int> = CAMERA_FPS_NORMAL
        var videoFps: Int = VIDEO_FPS_FREQUENCY_HIGH
        var iFrameInterval: Int = CameraEncoder.DEFAULT_KEY_I_FRAME_INTERVAL
        var bitrateMode: Int = CameraEncoder.DEFAULT_BITRATE_MODE

        @Suppress("unused")
        fun textureView(cameraTextureView: CameraTextureView) = apply { this.cameraTextureView = cameraTextureView }

        @Suppress("unused")
        fun previewInFullscreen(previewInFullscreen: Boolean) = apply { this.previewInFullscreen = previewInFullscreen }

        @Suppress("unused")
        fun quality(quality: Float) = apply { this.quality = quality }

        @Suppress("unused")
        fun cameraFps(cameraFps: Range<Int>) = apply { this.cameraFps = cameraFps }

        @Suppress("unused")
        fun videoFps(videoFps: Int) = apply { this.videoFps = videoFps }

        @Suppress("unused")
        fun iFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        @Suppress("unused")
        fun bitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }

        @Suppress("unused")
        fun build() = Camera2Component(this)
    }

    private var mDebugOutputH264 = false
    private var mDebugOutputYuv = false

    // Camera2 API supported the MAX width and height
    private val mCameraSupportedMaxPreviewWidth: Int
    private val mCameraSupportedMaxPreviewHeight: Int
    private var mCameraEncoder: CameraEncoder? = null
    private var mEncodeListener: EncodeDataUpdateListener? = null
    private var mLensSwitchListener: LensSwitchListener? = null
    private var mSupportFlash = false   // Support flash
    private var mTorchOn = false        // Flash continuously on

    // The context to process data
    private var mDataProcessContext: DataProcessContext =
        DataProcessFactory.getConcreteObject(DataProcessFactory.ENCODER_TYPE_NORMAL)!!
    var encoderType = DataProcessFactory.ENCODER_TYPE_NORMAL
        set(value) {
            mDataProcessContext = DataProcessFactory.getConcreteObject(value)
                ?: fail("unsupported encoding type=$value")
        }

    init {
        val screenSize = DeviceUtil.getResolution(builder.context)
        mCameraSupportedMaxPreviewWidth = max(screenSize.x, screenSize.y)
        mCameraSupportedMaxPreviewHeight = min(screenSize.x, screenSize.y)
    }
    // ============================

    private lateinit var mCameraId: String                      // Current using CameraId
    private var mLensFacing = LENS_FACING_BACK              // Lens facing. LENS_FACING_FRONT=0, LENS_FACING_BACK=1 = 0
    private var mCameraManager: CameraManager? = null           // The camera manager
    private var mCameraDevice: CameraDevice? = null             // Current camera device
    private var mCaptureSession: CameraCaptureSession? = null   // The configured capture session for a CameraDevice
    private var mSelectedSizeFromCamera: Size? = null           // Get the optimized size from camera supported size
    private var mPreviewSize: Size? = null                      // Camera preview size as well as the output video size
    private var mImageReader: ImageReader? = null               // The real-time data from Camera
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null  // The builder for capture requests
    private var mPreviewRequest: CaptureRequest? =
        null         // The capture request obtained from mPreviewRequestBuilder
    private var mOrientationListener: OrientationEventListener? = null // Device orientation listener
    private var mCameraThread: HandlerThread? = null            // Camera working thread
    private var mCameraHandler: Handler? = null                 // Camera working handler used by mCameraThread
    private val mCameraOpenCloseLock = Semaphore(1)      // The semaphore for camera status
    private lateinit var mCameraSupportedFpsRanges: Array<Range<Int>>    // Camera supported FPS range

    // ===== Debug code =======================
    private var mVideoYuvOs: BufferedOutputStream? = null
    private var mVideoH264Os: BufferedOutputStream? = null
    private var mBaseOutputFolder: String? = null
    // =========================================

    // Camera status callback
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()  // 1. Release a permit in semaphore
            mCameraDevice = cameraDevice    // 2. Current opening camera
            createCameraPreviewSession()    // 3. Create camera preview session
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()  // 1. Release a permit in semaphore
            cameraDevice.close()            // 2. Close camera
            mCameraDevice = null            // 3. Empty camera device
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()  // 1. Release a permit in semaphore
            cameraDevice.close()            // 2. Close camera
            mCameraDevice = null            // 3. Empty camera device
            builder.context.finish()        // 4. Finish current activity
        }
    }

    private val mOnImageAvailableListener = OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        if (image == null || mCameraHandler == null) return@OnImageAvailableListener

        mCameraHandler!!.post {
            try {
                val width = image.width
                val height = image.height
                CLog.v(TAG, "Image width=$width height=$height")

                if (mDebugOutputYuv) {
                    mVideoYuvOs?.write(mDataProcessContext.doProcess(image, mLensFacing))
                    return@post
                }

                val rotatedYuv420Data = mDataProcessContext.doProcess(image, mLensFacing)
                mCameraEncoder?.offerDataIntoQueue(rotatedYuv420Data)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image.close()
            }
        }
    }
    private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            CLog.i(TAG, "onSurfaceTextureAvailable width=$width, height=$height")
            openCamera(mLensFacing)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * Get the proper camera supported size.
     *
     * @param choices      Camera supported size list
     * @param screenWidth  Screen width
     * @param screenHeight Screen height
     * @return The proper camera supported size
     */
    private fun chooseProperSize(
        choices: Array<Size>,
        screenWidth: Int,
        screenHeight: Int,
        swappedDimensions: Boolean
    ): Size {
        val bigEnough: MutableList<Size> = ArrayList()
        val stringBuilder = StringBuilder()
        if (swappedDimensions) { // Portrait
            for (option in choices) {
                val str = "[" + option.width + ", " + option.height + "]"
                stringBuilder.append(str)
                if (option.height != screenWidth || option.width > screenHeight) continue
                bigEnough.add(option)
            }
        } else { // Landscape
            for (option in choices) {
                val str = "[" + option.width + ", " + option.height + "]"
                stringBuilder.append(str)
                if (option.width != screenHeight || option.height > screenWidth) continue
                bigEnough.add(option)
            }
        }
        CLog.i(TAG, "chooseProperSize: $stringBuilder")
        return if (bigEnough.size > 0) {
            Collections.max(bigEnough, CompareSizesByArea())
        } else {
            CLog.e(TAG, "Couldn't find any suitable preview size")
            choices[choices.size / 2]
        }
    }

    /**
     * Debug ONLY
     */
    fun initDebugOutput() {
        try {
            if (mDebugOutputH264 || mDebugOutputYuv) {
                mBaseOutputFolder =
                    builder.context.getExternalFilesDir(null)!!.absolutePath + File.separator + "leo-media"
                val folder = File(mBaseOutputFolder!!)
                if (!folder.exists()) {
                    val mkdirStatus = folder.mkdirs()
                    CLog.d(TAG, "$mBaseOutputFolder=$mkdirStatus")
                }
                if (mDebugOutputYuv) {
                    val videoYuvFile = File(mBaseOutputFolder, "camera.yuv")
                    mVideoYuvOs = BufferedOutputStream(FileOutputStream(videoYuvFile))
                }
                if (!mDebugOutputYuv && mDebugOutputH264) {
                    val videoH264File = File(mBaseOutputFolder, "camera.h264")
                    mVideoH264Os = BufferedOutputStream(FileOutputStream(videoH264File))
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
            if (mDebugOutputYuv) {
                CLog.i(TAG, "release videoYuvOs")
                mVideoYuvOs?.flush()
                mVideoYuvOs?.close()
            }
            if (mDebugOutputH264) {
                CLog.i(TAG, "release videoH264Os")
                mVideoH264Os?.flush()
                mVideoH264Os?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openCameraAndGetData(lensFacing: Int) {
        CLog.w(TAG, "encodingType=$encoderType")

        mLensFacing = lensFacing
        startCameraThread()

        // If current CameraTextureView is still available(For instance, you move app to background
        // and back again in a short time or make screen off then on), the onSurfaceTextureAvailable callback
        // will not be called. So we just need to openCamera directly. Otherwise, we need to wait for the
        // SurfaceTexture listener.
        if (builder.cameraTextureView != null) {
            CLog.w(TAG, "With CameraTextureView")
            if (builder.cameraTextureView!!.isAvailable) {
                CLog.i(TAG, "Camera is ready.")
                openCamera(lensFacing)
            } else {
                CLog.w(TAG, "Camera is NOT ready.")
                builder.cameraTextureView!!.surfaceTextureListener = mSurfaceTextureListener
            }
        } else {
            CLog.w(TAG, "Without CameraTextureView")
            openCamera(lensFacing)
        }

        // Clockwise orientation: 0, 90, 180, 270
        // 0: Normal portrait
        mOrientationListener = object : OrientationEventListener(
            builder.context,
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
        if (mOrientationListener!!.canDetectOrientation()) {
            mOrientationListener!!.enable()
        } else {
            mOrientationListener!!.disable()
        }
    }

    fun closeCameraAndStopRecord() {
        try {
            closeCamera()
            stopCameraThread()
            CLog.i(TAG, "yuv2H264Encoder.release()")
            mCameraEncoder?.release()
            mOrientationListener?.disable()

            mCameraEncoder = null
            mOrientationListener = null
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
    private fun openCamera(lensFacing: Int) {
        // Config camera output
        configCameraOutput(lensFacing)
        initCameraEncoder(
            mPreviewSize!!.width, mPreviewSize!!.height,
            (mPreviewSize!!.width * mPreviewSize!!.height * builder.quality).toInt(),
            builder.videoFps, builder.iFrameInterval, builder.bitrateMode
        )
        mCameraManager = builder.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Try to acquire camera permit in 2500ms.
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            mCameraManager?.openCamera(mCameraId, mStateCallback, mCameraHandler)
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
        mCameraEncoder = CameraEncoder(width, height, bitrate, frameRate, iFrameInterval, bitrateMode)
        checkNotNull(mCameraEncoder) { "CameraEncoder can not be null" }.setDataUpdateCallback(object :
            CallbackListener {
            override fun onCallback(h264Data: ByteArray) {
                mEncodeListener?.onUpdate(h264Data)
                if (mDebugOutputH264) mVideoH264Os?.write(h264Data)
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
        val manager = builder.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        try {
            // Iterate all camera list
            for (cameraId in checkNotNull(manager) { "CameraManager can not be null" }.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing != lensFacing) {
                    continue
                }
                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                val isFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                CLog.w(TAG, "isFlashSupported=$isFlashSupported")
                this.mSupportFlash = isFlashSupported ?: false

                // LEVEL_3(3) > FULL(1) > LIMIT(0) > LEGACY(2)
                val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                CLog.w(TAG, "hardwareLevel=$hardwareLevel")

                // Get camera supported fps. It will be used to create CaptureRequest
                mCameraSupportedFpsRanges =
                    characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
                CLog.w(TAG, "mCameraSupportedFpsRanges=${mCameraSupportedFpsRanges.contentToString()}")

                // Generally, if the device is in portrait(Surface.ROTATION_0),
                // the camera SENSOR_ORIENTATION(90) is just in landscape and vice versa.
                val deviceRotation = builder.context.windowManager.defaultDisplay.rotation
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
                if (cameraWidth > mCameraSupportedMaxPreviewHeight) cameraWidth = mCameraSupportedMaxPreviewHeight
                if (cameraHeight > mCameraSupportedMaxPreviewWidth) cameraHeight = mCameraSupportedMaxPreviewWidth
                CLog.w(TAG, "After adjust cameraWidth=$cameraWidth, cameraHeight=$cameraHeight")

                // Calculate ImageReader input preview size from supported size list by camera.
                // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
                // Attention: The returned value is in camera orientation. NOT in device orientation.
                mSelectedSizeFromCamera = chooseProperSize(
                    configMap.getOutputSizes(SurfaceTexture::class.java),
                    cameraWidth, cameraHeight, swapDimension
                )

                // Take care of the result value. It's in camera orientation.
                CLog.w(
                    TAG,
                    "mSelectedSizeFromCamera width=${mSelectedSizeFromCamera!!.width} height=${mSelectedSizeFromCamera!!.height}"
                )

                // Swap the mSelectedPreviewSizeFromCamera is necessary. So that we can use the proper size for CameraTextureView.
                mPreviewSize =
                    if (swapDimension) Size(mSelectedSizeFromCamera!!.height, mSelectedSizeFromCamera!!.width) else {
                        mSelectedSizeFromCamera
                    }
                CLog.w(TAG, "mPreviewSize width=${mPreviewSize!!.width} height=${mPreviewSize!!.height}")

                // If you do not set CameraTextureView dimension, it will be in full screen by default.
                if (builder.cameraTextureView != null && !builder.previewInFullscreen) {
                    builder.cameraTextureView!!.setDimension(mPreviewSize!!.width, mPreviewSize!!.height)
                }

                // The input camera size must be camera supported size.
                // Otherwise, the video will be distorted.
                // The first and second parameters must be supported camera size NOT preview size.
                // That means width is greater then height by default.
                mImageReader = ImageReader.newInstance(
                    mSelectedSizeFromCamera!!.width,
                    mSelectedSizeFromCamera!!.height,
                    ImageFormat.YUV_420_888 /*ImageFormat.JPEG*/,
                    2
                )
                mImageReader!!.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler)
                mCameraId = cameraId
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
            surfaceList.add(mImageReader!!.surface)
            if (builder.cameraTextureView != null) {
                val texture: SurfaceTexture? = builder.cameraTextureView!!.surfaceTexture
                texture?.setDefaultBufferSize(mSelectedSizeFromCamera!!.width, mSelectedSizeFromCamera!!.height)
                // Get the surface for output
                texture?.let {
                    val surface = Surface(it)
                    surfaceList.add(surface)
                }
            }

            // Create CaptureRequest
            // https://blog.csdn.net/afei__/article/details/86326991
            // CameraDevice.TEMPLATE_PREVIEW
            // CameraDevice.TEMPLATE_STILL_CAPTURE
            // CameraDevice.TEMPLATE_RECORD
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            for (surface in surfaceList) {
                mPreviewRequestBuilder!!.addTarget(surface)
            }
            mCameraDevice!!.createCaptureSession(
                surfaceList,
                object : CameraCaptureSession.StateCallback() {
                    // Create capture session
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // When camera is closed, return directly.
                        if (null == mCameraDevice) {
                            return
                        }
                        mCaptureSession = cameraCaptureSession
                        try {
                            // AWB
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);

                            // Auto focus
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                            )
                            // Auto exposure. The flash will be open automatically in dark.
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )

//                                int rotation=getWindowManager().getDefaultDisplay().getRotation();
//                                mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

                            // Set camera fps according to mCameraSupportedFpsRanges[mCameraSupportedFpsRanges.length-1]
                            CLog.w(TAG, "Camera FPS=${builder.cameraFps}")
                            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, builder.cameraFps)
                            mPreviewRequest = mPreviewRequestBuilder!!.build()
                            // Request endlessly repeating capture of images by this capture session.
                            // With this method, the camera device will continually capture images
                            // using the settings in the provided CaptureRequest,
                            // at the maximum rate possible.
                            mCaptureSession!!.setRepeatingRequest(mPreviewRequest!!, null, mCameraHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        Toast.makeText(builder.context, "createCaptureSession Failed", Toast.LENGTH_SHORT).show()
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        CLog.i(TAG, "closeCamera()")
        try {
            mCameraOpenCloseLock.acquire()

            mCaptureSession?.close()
            mCaptureSession = null

            mCameraDevice?.close()
            mImageReader?.close()

            mCameraDevice = null
            mImageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private fun startCameraThread() {
        mCameraThread = HandlerThread("camera-background")
        mCameraThread!!.start()
        mCameraHandler = Handler(mCameraThread!!.looper)
    }

    private fun stopCameraThread() {
        CLog.i(TAG, "stopCameraThread()")
        try {
            if (mCameraThread != null) {
                mCameraThread!!.quitSafely()
                mCameraThread!!.join()
                mCameraThread = null
            }
            if (mCameraHandler != null) {
                mCameraHandler!!.removeCallbacksAndMessages(null)
                mCameraHandler = null
            }
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
        mEncodeListener = listener
    }

    fun setLensSwitchListener(listener: LensSwitchListener) {
        mLensSwitchListener = listener
    }

    @Suppress("unchecked")
    fun switchCamera(lensFacing: Int) {
        if (mCameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

//        Toast.makeText(mContext, "switchCamera=" + lensFacing, Toast.LENGTH_SHORT).show();
        closeCamera()
        mLensFacing = lensFacing
        openCamera(lensFacing)
        mLensSwitchListener?.onSwitch(lensFacing)
    }

    fun switchCamera() {
        if (mCameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

        if (mLensFacing == LENS_FACING_BACK) {
            switchToFrontCamera()
        } else {
            switchToBackCamera()
        }
    }

    @Suppress("unchecked")
    fun switchToBackCamera() {
        if (mCameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

        switchCamera(LENS_FACING_BACK)
    }

    @Suppress("unchecked")
    fun switchToFrontCamera() {
        if (mCameraDevice == null) {
            throw IllegalAccessError("You must call openCameraAndGetData(lensFacing: Int) first.")
        }

        switchCamera(LENS_FACING_FRONT)
    }

    fun switchFlash() {
        try {
            if (LENS_FACING_BACK == mLensFacing && mSupportFlash) {
                if (mTorchOn) {
                    mTorchOn = false

                    // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
                    // Otherwise the flash will not be off.
                    mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    mPreviewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
                    mPreviewRequest = mPreviewRequestBuilder?.build()
                    mCaptureSession?.setRepeatingRequest(mPreviewRequest!!, null, mCameraHandler)

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        mCameraManager.setTorchMode(mCameraId, false);
//                    }
                    CLog.w(TAG, "Flash OFF")
                } else {
                    mTorchOn = true

                    // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
                    // Otherwise the flash will not be on.
                    mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    mPreviewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);
                    mPreviewRequest = mPreviewRequestBuilder?.build()
                    mCaptureSession?.setRepeatingRequest(mPreviewRequest!!, null, mCameraHandler)

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
        mDebugOutputH264 = outputH264
    }

    /**
     * Notice that, if you do allow to output YUV. ONLY the YUV file will be outputted.
     * The H264 file will not be created no matter what you set for setDebugOutputH264()
     *
     * @param outputYuv Whether to output YUV
     */
    @Suppress("unused")
    fun setDebugOutputYuv(outputYuv: Boolean) {
        mDebugOutputYuv = outputYuv
    }

    companion object {
        private const val TAG = "Camera2Component"

        @Suppress("unused")
        const val BITRATE_INSANE_HIGH = 8f

        @Suppress("unused")
        const val BITRATE_EXTREME_HIGH = 5f

        @Suppress("unused")
        const val BITRATE_VERY_HIGH = 3f

        @Suppress("unused")
        const val BITRATE_HIGH = 2f

        @Suppress("unused")
        const val BITRATE_NORMAL = 1f

        @Suppress("unused")
        const val BITRATE_LOW = 0.75f

        @Suppress("unused")
        const val BITRATE_VERY_LOW = 0.5f

        @Suppress("unused")
        @JvmField
        val CAMERA_FPS_VERY_HIGH = Range(30, 30)    // [30, 30]

        @Suppress("unused")
        @JvmField
        val CAMERA_FPS_HIGH = Range(24, 24)         // [24, 24]

        @Suppress("unused")
        @JvmField
        val CAMERA_FPS_NORMAL = Range(20, 20)       // [20, 20]

        @Suppress("unused")
        @JvmField
        val CAMERA_FPS_LOW = Range(15, 15)          // [15, 15]

        @Suppress("unused")
        const val VIDEO_FPS_VERY_HIGH = 25

        @Suppress("unused")
        const val VIDEO_FPS_FREQUENCY_HIGH = 20

        @Suppress("unused")
        const val VIDEO_FPS_FREQUENCY_NORMAL = 15

        @Suppress("unused")
        const val VIDEO_FPS_FREQUENCY_LOW = 10

        @Suppress("unused")
        const val VIDEO_FPS_FREQUENCY_VERY_LOW = 5
    }
}