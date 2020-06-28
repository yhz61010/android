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
import android.view.Surface
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ho1ho.androidbase.exts.computeExifOrientation
import com.ho1ho.androidbase.exts.fail
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.camera2live.view.CameraSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Author: Michael Leo
 * Date: 20-6-24 下午5:05
 */
class CameraPhotoFragmentHelper(
    private val context: Fragment,
    private var lensFacing: Int,
    private val cameraSurfaceView: CameraSurfaceView,
    private val overlay: View
) {
    private lateinit var cameraId: String
    private var lensSwitchListener: LensSwitchListener? = null

    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = context.requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    lateinit var characteristics: CameraCharacteristics

    init {
        initializeParameters()
    }

    private fun initializeParameters() {
        cameraId = if (CameraMetadata.LENS_FACING_BACK == lensFacing) "0" else "1"
        characteristics = cameraManager.getCameraCharacteristics(cameraId)
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
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
    fun initializeCamera() = context.lifecycleScope.launch(Dispatchers.Main) {
        initializeParameters()

        // Open the selected camera
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        // Initialize an image reader which will be used to capture still photos
        val size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
            .maxBy { it.height * it.width }!!
        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(cameraSurfaceView.holder.surface, imageReader.surface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        captureRequestBuilder = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE
        ).apply { addTarget(cameraSurfaceView.holder.surface) }

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(captureRequestBuilder.build(), null, cameraHandler)
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
                cameraSurfaceView.post(animationTask)
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
                        val rotation = (context as CameraPhotoFragment).relativeOrientation.value ?: 0
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

        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise the flash will not be on.
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT);
        val captureRequest = captureRequestBuilder.build()
        session.setRepeatingRequest(captureRequest, null, cameraHandler)

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        mCameraManager.setTorchMode(mCameraId, true);
//                    }
        Log.w(TAG, "Flash ON")
    }

    fun turnOffFlash() {
        if (!::camera.isInitialized || !::session.isInitialized) {
            throw IllegalAccessError("You must initialize camera and session first.")
        }

        // On Samsung, you must also set CONTROL_AE_MODE to CONTROL_AE_MODE_ON.
        // Otherwise the flash will not be off.
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        //                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
        val captureRequest = captureRequestBuilder.build()
        session.setRepeatingRequest(captureRequest, null, cameraHandler)

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        mCameraManager.setTorchMode(mCameraId, false);
//                    }
        Log.w(TAG, "Flash OFF")
    }

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

//        Toast.makeText(mContext, "switchCamera=" + lensFacing, Toast.LENGTH_SHORT).show();
        closeCamera()
        this.lensFacing = lensFacing
        initializeCamera()
        lensSwitchListener?.onSwitch(lensFacing)
    }

    fun closeCamera() {
        CLog.i(TAG, "closeCamera()")

        try {
            if (::session.isInitialized) session.close()
            if (::camera.isInitialized) camera.close()
            if (::imageReader.isInitialized) imageReader.close()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.", e)
        }
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

    fun stop() {
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    fun release() {
        cameraHandler.removeCallbacksAndMessages(null)
        cameraThread.quitSafely()
        imageReaderHandler.removeCallbacksAndMessages(null)
        imageReaderThread.quitSafely()
    }

    companion object {
        private val TAG = CameraPhotoFragmentHelper::class.java.simpleName

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
    }

}