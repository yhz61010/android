package com.leovp.camerax_sdk.fragments.base

import android.content.ContentUris
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.*
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import coil.load
import coil.transform.CircleCropTransformation
import com.google.common.util.concurrent.ListenableFuture
import com.leovp.camerax_sdk.R
import com.leovp.camerax_sdk.adapter.Media
import com.leovp.camerax_sdk.bean.CaptureImage
import com.leovp.camerax_sdk.listeners.CameraXTouchListener
import com.leovp.camerax_sdk.utils.*
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 2022/4/25 10:26
 */
abstract class BaseCameraXFragment<B : ViewBinding> : Fragment() {
    abstract fun getTagName(): String
    val logTag: String by lazy { getTagName() }

    /** Generic ViewBinding of the subclasses */
    lateinit var binding: B

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): B

    protected lateinit var outputDirectory: File

    // Selector showing which camera is selected (front or back)
    protected var lensFacing: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    protected var hdrCameraSelector: CameraSelector? = null
    protected var preview: Preview? = null
    protected var camera: Camera? = null
    protected var cameraProvider: ProcessCameraProvider? = null

    //    abstract fun getPreviewView(): PreviewView
    //    private val viewFinder: PreviewView by lazy { getPreviewView() }

    /** Blocking camera operations are performed using this executor */
    protected val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy { requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    protected var displayId: Int = -1
    protected val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    protected val soundManager by lazy { SoundManager.getInstance(requireContext()) }
    protected var touchListener: CameraXTouchListener? = null

    /** Returns true if the device has an available back camera. False otherwise */
    protected fun hasBackCamera(): Boolean = cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false

    /** Returns true if the device has an available front camera. False otherwise */
    protected fun hasFrontCamera(): Boolean = cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Determine the output directory
        outputDirectory = getOutputPictureDirectory(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        lifecycleScope.launch { soundManager.loadSounds() }
        binding = getViewBinding(inflater, container, savedInstanceState)
        return binding.root
    }

    override fun onDestroyView() {
        soundManager.release()
        // Shut down our background executor
        cameraExecutor.shutdown()
        super.onDestroyView()
    }

    protected fun captureForBytes(imageCapture: ImageCapture, onImageSaved: (savedImage: CaptureImage) -> Unit) {
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val imageBuffer = image.planes[0].buffer
                val width = image.width
                val height = image.height
                val imageBytes = ByteArray(imageBuffer.remaining()).apply { imageBuffer.get(this) }
                // DO NOT forget for close Image object
                image.close()
                onImageSaved(CaptureImage(imageBytes, width, height))
            }

            override fun onError(exc: ImageCaptureException) {
                LogContext.log.e(logTag, "ImageCapturedCallback - Photo capture failed: ${exc.message}", exc)
            }
        })
    }

    protected fun captureForOutputFile(imageCapture: ImageCapture,
        outputDirectory: File,
        onImageSaved: (uri: Uri) -> Unit) {
        // Create output file to hold the image
        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

        // Setup image capture metadata
        val metadata = ImageCapture.Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(metadata)
            .build()

        // Setup image capture listener which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    LogContext.log.e(logTag, "ImageSavedCallback - Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    soundManager.playShutterSound()
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    onImageSaved(savedUri)
                }
            })
    }

    protected suspend fun configCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()
        // Select lensFacing depending on the available cameras
        lensFacing = when {
            hasBackCamera()  -> CameraSelector.DEFAULT_BACK_CAMERA
            hasFrontCamera() -> CameraSelector.DEFAULT_FRONT_CAMERA
            else             -> throw IllegalStateException("Back and front camera are unavailable")
        }
    }

    protected fun initCameraGesture(viewFinder: PreviewView, camera: Camera) {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio: Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1F
                val delta = detector.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                LogContext.log.d(logTag,
                    "currentZoomRatio=$currentZoomRatio delta=$delta New zoomRatio=${currentZoomRatio * delta}")
                return true
            }
        }

        val gestureListener: GestureDetector.SimpleOnGestureListener =
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        LogContext.log.i(logTag, "Double click to zoom.")
                        val zoomState: LiveData<ZoomState> = camera.cameraInfo.zoomState
                        val currentZoomRatio: Float = zoomState.value?.zoomRatio ?: 0f
                        val minZoomRatio: Float = zoomState.value?.minZoomRatio ?: 0f
                        if (currentZoomRatio > minZoomRatio) {
                            camera.cameraControl.setLinearZoom(0f)
                        } else {
                            camera.cameraControl.setLinearZoom(0.5f)
                        }
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        LogContext.log.i(logTag, "Single tap to focus.")
                        val factory = viewFinder.meteringPointFactory
                        val point = factory.createPoint(e.x, e.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(5, TimeUnit.SECONDS)
                            .build()
                        touchListener?.onStartFocusing(e.rawX, e.rawY)
                        val focusFuture: ListenableFuture<FocusMeteringResult> =
                                camera.cameraControl.startFocusAndMetering(action)
                        focusFuture.addListener({
                            runCatching {
                                // Get focus result
                                val result = focusFuture.get() as FocusMeteringResult
                                if (result.isFocusSuccessful) {
                                    touchListener?.onFocusSuccess()
                                } else {
                                    touchListener?.onFocusFail()
                                }
                            }
                        }, ContextCompat.getMainExecutor(requireContext()))
                        return true
                    }

                    // ====================

                    override fun onFling(e1: MotionEvent?,
                        e2: MotionEvent?,
                        velocityX: Float,
                        velocityY: Float): Boolean {
                        if (e1 == null || e2 == null) return super.onFling(e1, e2, velocityX, velocityY)
                        val deltaX = e1.x - e2.x
                        val deltaXAbs = abs(deltaX)

                        if (deltaXAbs >= MIN_SWIPE_DISTANCE) {
                            if (deltaX > 0) {
                                swipeCallback?.onLeftSwipe()
                            } else {
                                swipeCallback?.onRightSwipe()
                            }
                        }

                        val deltaY = e1.y - e2.y
                        val deltaYAbs = abs(deltaY)

                        //                        LogContext.log.v(logTag, "deltaX=$deltaX deltaY=$deltaY")

                        if (deltaYAbs >= MIN_SWIPE_DISTANCE) {
                            if (deltaY > 0) {
                                swipeCallback?.onUpSwipe()
                            } else {
                                swipeCallback?.onDownSwipe()
                            }
                        }

                        return true
                    }
                }

        val scaleGestureDetector = ScaleGestureDetector(viewFinder.context, listener)
        val gestureDetector = GestureDetector(viewFinder.context, gestureListener)

        viewFinder.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) gestureDetector.onTouchEvent(event)
            view.performClick()
            true
        }
    }

    private var swipeCallback: SwipeCallback? = null

    fun setSwipeCallback(left: () -> Unit = {}, right: () -> Unit = {}, up: () -> Unit = {}, down: () -> Unit = {}) {
        swipeCallback = object : SwipeCallback {
            override fun onUpSwipe() {
                up()
            }

            override fun onDownSwipe() {
                down()
            }

            override fun onLeftSwipe() {
                left()
            }

            override fun onRightSwipe() {
                right()
            }
        }
    }

    interface SwipeCallback {
        fun onUpSwipe()

        fun onDownSwipe()

        fun onLeftSwipe()

        fun onRightSwipe()
    }

    protected fun checkForHdrExtensionAvailability(hasHdr: Boolean, callback: (isHdrAvailable: Boolean) -> Unit) {
        // Create a Vendor Extension for HDR
        val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(requireContext(), cameraProvider ?: return)
        extensionsManagerFuture.addListener({
            val extensionsManager = extensionsManagerFuture.get() ?: return@addListener
            val isAvailable = extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.HDR)

            // Check for any extension availability
            LogContext.log.w(logTag, "AUTO " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.AUTO))
            LogContext.log.w(logTag, "HDR " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.HDR))
            LogContext.log.w(logTag,
                "FACE RETOUCH " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.FACE_RETOUCH))
            LogContext.log.w(logTag, "BOKEH " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.BOKEH))
            LogContext.log.w(logTag, "NIGHT " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.NIGHT))
            LogContext.log.w(logTag, "NONE " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.NONE))

            // Check if the extension is available on the device
            if (!isAvailable) {
                callback(false)
            } else if (hasHdr) {
                callback(true)
                hdrCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(lensFacing, ExtensionMode.HDR)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     *  [androidx.camera.core.ImageAnalysis.Builder] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    protected fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    protected fun setGalleryThumbnail(uri: Uri, galleryButton: ImageButton) {
        // Run the operations in the view's thread
        galleryButton.let { photoViewButton ->
            photoViewButton.post {
                photoViewButton.setPadding(resources.getDimension(R.dimen.stroke_tiny).toInt())
                photoViewButton.load(uri) {
                    // placeholder(R.drawable.ic_photo)
                    error(R.drawable.ic_photo)
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    protected fun outputCameraParameters(camSelector: CameraSelector, desiredVideoWidth: Int, desiredVideoHeight: Int) =
            runCatching {
                if (cameraProvider?.hasCamera(camSelector) == true) {
                    val cameraId = if (CameraSelector.DEFAULT_BACK_CAMERA == camSelector) "0" else "1"
                    val characteristics: CameraCharacteristics =
                            cameraManager.getCameraCharacteristics(cameraId)
                    val configMap = characteristics.getConfigMap()

                    val isFlashSupported = characteristics.isFlashSupported()
                    // LEVEL_3(3) > FULL(1) > LIMIT(0) > LEGACY(2)
                    val hardwareLevel = characteristics.hardwareLevel()
                    // Get camera supported fps. It will be used to create CaptureRequest
                    val cameraSupportedFpsRanges: Array<Range<Int>> = characteristics.supportedFpsRanges()
                    val highSpeedVideoFpsRanges = configMap.highSpeedVideoFpsRanges
                    val highSpeedVideoSizes = configMap.highSpeedVideoSizes

                    val allCameraSupportSize = configMap.getOutputSizes(SurfaceHolder::class.java)

                    // Calculate ImageReader input preview size from supported size list by camera.
                    // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
                    // Attention: The returned value is in camera orientation. NOT in device orientation.
                    val previewSize: Size =
                            getSpecificPreviewOutputSize(requireContext(),
                                desiredVideoWidth,
                                desiredVideoHeight,
                                characteristics)

                    val cameraParametersString = """Camera Info:
               cameraId=${if (cameraId == "0") "BACK" else "FRONT"}
         deviceRotation=${requireContext().getDeviceRotation()}
cameraSensorOrientation=${characteristics.cameraSensorOrientation()}
       isFlashSupported=$isFlashSupported
          hardwareLevel=$hardwareLevel

 Supported color format for  AVC=${
                        getSupportedColorFormatForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).sorted()
                            .joinToString(",")
                    }
 Supported color format for HEVC=${
                        getSupportedColorFormatForEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC).sorted()
                            .joinToString(",")
                    }

Supported profile/level for  AVC=${getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).joinToString(",") { "${it.profile}/${it.level}" }}
Supported profile/level for HEVC=${getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).joinToString(",") { "${it.profile}/${it.level}" }}

     highSpeedVideoFpsRanges=${highSpeedVideoFpsRanges?.contentToString()}
         highSpeedVideoSizes=${highSpeedVideoSizes?.contentToString()}

    cameraSupportedFpsRanges=${cameraSupportedFpsRanges.contentToString()}
        allCameraSupportSize=${allCameraSupportSize?.contentToString()}

               Desired dimen=${desiredVideoWidth}x$desiredVideoHeight
           previewSize dimen=${previewSize.width}x${previewSize.height}
        """.trimIndent()
                    LogContext.log.w(logTag, cameraParametersString)
                    LogContext.log.w(logTag, "==================================================")
                }
            }.onFailure { LogContext.log.i(logTag, "outputCameraParameters error.", it) }

    protected fun getMedia(): List<Media> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getMediaQPlus()
    } else {
        getMediaQMinus()
    }.reversed()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getMediaQPlus(): List<Media> {
        val items = mutableListOf<Media>()
        val contentResolver = requireContext().applicationContext.contentResolver

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DATE_TAKEN,
            ),
            null,
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                if (path == outputDirectory.absolutePath) {
                    items.add(Media(contentUri, true, date))
                }
            }
        }

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_TAKEN,
            ),
            null,
            null,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                if (path == outputDirectory.absolutePath) {
                    items.add(Media(contentUri, false, date))
                }
            }
        }
        return items
    }

    private fun getMediaQMinus(): List<Media> {
        val items = mutableListOf<Media>()

        outputDirectory.listFiles()?.forEach {
            val authority = requireContext().applicationContext.packageName + ".provider"
            val mediaUri = FileProvider.getUriForFile(requireContext(), authority, it)
            items.add(Media(mediaUri, it.extension == "mp4", it.lastModified()))
        }

        return items
    }

    companion object {
        internal const val FILENAME = "yyyyMMdd-HHmmss-SSS"

        internal const val BASE_FOLDER_NAME = "CameraX"

        internal const val KEY_FLASH = "camerax-flash"
        internal const val KEY_GRID = "camerax-grid"
        internal const val KEY_HDR = "camerax-hdr"

        internal const val MIN_SWIPE_DISTANCE = 400

        /** Milliseconds used for UI animations */
        internal const val ANIMATION_FAST_MILLIS = 50L
        internal const val ANIMATION_SLOW_MILLIS = 100L
        internal const val PHOTO_EXTENSION = ".jpg"
        internal const val RATIO_4_3_VALUE = 4.0 / 3.0
        internal const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        internal fun createFile(baseFolder: File,
            @Suppress("SameParameterValue") format: String,
            @Suppress("SameParameterValue") extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension)

        internal fun getOutputPictureDirectory(context: Context, parentFolder: String = BASE_FOLDER_NAME): File {
            return File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), parentFolder).also {
                if (!it.exists()) it.mkdirs()
            }
        }

        internal fun getOutputMovieDirectory(context: Context, parentFolder: String = BASE_FOLDER_NAME): File {
            return File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), parentFolder).also {
                if (!it.exists()) it.mkdirs()
            }
        }
    }
}