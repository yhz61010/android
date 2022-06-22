package com.leovp.camerax_sdk.fragments.base

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
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
import com.leovp.camerax_sdk.databinding.IncRatioOptionsBinding
import com.leovp.camerax_sdk.enums.CameraRatio
import com.leovp.camerax_sdk.listeners.CameraXTouchListener
import com.leovp.camerax_sdk.utils.*
import com.leovp.lib_common_android.exts.dp2px
import com.leovp.lib_common_android.exts.getRealResolution
import com.leovp.lib_common_android.exts.topMargin
import com.leovp.lib_common_kotlin.exts.getRatio
import com.leovp.lib_common_kotlin.exts.round
import com.leovp.lib_image.flipRotate
import com.leovp.lib_image.recycledSafety
import com.leovp.lib_image.toBytes
import com.leovp.lib_image.writeToFile
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Author: Michael Leo
 * Date: 2022/4/25 10:26
 */

//typealias OrientationListener = (Int) -> Unit

abstract class BaseCameraXFragment<B : ViewBinding> : Fragment() {
    abstract fun getTagName(): String
    val logTag: String by lazy { getTagName() }

    /** Generic ViewBinding of the subclasses */
    lateinit var binding: B

    abstract fun getViewBinding(inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): B

    // An instance of a helper function to work with Shared Preferences
    protected val prefs by lazy { SharedPrefsManager.getInstance(requireContext()) }
    protected lateinit var outputPictureDirectory: File
    protected lateinit var outputVideoDirectory: File

    // Selector showing which camera is selected (front or back)
    // Default value is Back Camera.
    protected var lensFacing: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    protected var hdrCameraSelector: CameraSelector? = null
    protected var preview: Preview? = null
    protected var camera: Camera? = null
    protected var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    protected val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    protected val cameraManager: CameraManager by lazy { requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    protected var displayId: Int = -1

    /**
     *  We have already set `android:screenOrientation` to "userPortrait".
     * So we don't need to monitor the orientation.
     *
     * https://developer.android.com/training/camerax/orientation-rotation#displayListener
     */
    //    protected val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    protected val soundManager by lazy { SoundManager.getInstance(requireContext()) }
    protected var touchListener: CameraXTouchListener? = null

    /** Returns true if the device has an available back camera. False otherwise */
    protected fun hasBackCamera(): Boolean =
            cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false

    /** Returns true if the device has an available front camera. False otherwise */
    protected fun hasFrontCamera(): Boolean =
            cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false

    // This property will be initialize in updateOrientationLiveData()
    protected var cameraRotationInDegree = -1
    //    var deviceOrientationListener: OrientationListener? = null

    /** Live data listener for changes in the device orientation relative to the camera */
    private var relativeOrientation: OrientationLiveData? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        LogContext.log.w(logTag, "=====> onViewCreated <=====")
        super.onViewCreated(view, savedInstanceState)

        lensFacing = loadCameraLensFacing()

        // Determine the output directory
        outputPictureDirectory = getOutputPictureDirectory(requireContext())
        outputVideoDirectory = getOutputVideoDirectory(requireContext())
    }

    private fun updateOrientationLiveData() {
        relativeOrientation?.removeObservers(viewLifecycleOwner)
        relativeOrientation = null

        val cameraId = if (CameraSelector.DEFAULT_BACK_CAMERA == lensFacing) "0" else "1"
        val cameraName = if (cameraId == "0") "BACK" else "FRONT"
        LogContext.log.d(logTag,
            "updateOrientationLiveData cameraId: $cameraName")
        val characteristics: CameraCharacteristics =
                cameraManager.getCameraCharacteristics(cameraId)
        cameraRotationInDegree = characteristics.cameraSensorOrientation()
        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner) { cameraRotation ->
                LogContext.log.i(logTag,
                    "$cameraName cameraSensorOrientation changed to: $cameraRotation")
                cameraRotationInDegree = cameraRotation
                //                deviceOrientationListener?.invoke(cameraRotation)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        LogContext.log.w(logTag, "=====> onCreateView <=====")
        lifecycleScope.launch { soundManager.loadSounds() }
        binding = getViewBinding(inflater, container, savedInstanceState)
        return binding.root
    }

    override fun onResume() {
        LogContext.log.w(logTag, "=====> onResume() <=====")
        super.onResume()
        updateOrientationLiveData()
    }

    override fun onPause() {
        super.onPause()
        relativeOrientation?.removeObservers(viewLifecycleOwner)
        relativeOrientation = null
    }

    override fun onDestroyView() {
        LogContext.log.w(logTag, "=====> onDestroyView() <=====")
        super.onDestroyView()
    }

    override fun onDestroy() {
        LogContext.log.w(logTag, "=====> onDestroy() <=====")
        soundManager.release()
        // Shut down our background executor
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    /**
     * @param onImageSaved It guarantees that if the exc property in this function is not null,
     * the savedImage property must be null.
     * On the contrary, if the savedImage property in this function is not null,
     * the exc property must be null.
     */
    protected fun captureForBytes(viewFinder: PreviewView,
        imageCapture: ImageCapture,
        startTimestamp: Long,
        onImageSaved: (savedImage: CaptureImage.ImageBytes?, exc: Exception?) -> Unit) {
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                LogContext.log.w(logTag,
                    "Capture image bytes cost=${System.currentTimeMillis() - startTimestamp}ms")

                try {
                    showShutterAnimation(viewFinder)
                    soundManager.playShutterSound()

                    val st0 = System.currentTimeMillis()
                    // For takePicture, the ImageProxy only contains one plane AKA Y plane.
                    val imageBuffer = image.planes[0].buffer
                    //                val width = image.width
                    //                val height = image.height
                    val oriImageBytes = imageBuffer.toByteArray()
                    LogContext.log.w(logTag,
                        "Get bytes from ImageProxy=${System.currentTimeMillis() - st0}ms")

                    // DO NOT forget for close Image object
                    image.close()

                    lifecycleScope.launch(Dispatchers.IO) {
                        val mirror = CameraSelector.DEFAULT_FRONT_CAMERA == lensFacing
                        val st1 = System.currentTimeMillis()
                        val oriBmp =
                                BitmapFactory.decodeByteArray(oriImageBytes, 0, oriImageBytes.size)
                        val st2 = System.currentTimeMillis()
                        LogContext.log.w(logTag, "Decode bitmap bytes cost=${st2 - st1}ms")
                        val processedBmp =
                                adjustBitmapRotation(oriBmp, mirror, cameraRotationInDegree)
                        val st3 = System.currentTimeMillis()
                        LogContext.log.w(logTag, "Mirror and rotate bitmap cost=${st3 - st2}ms")
                        val finalWidth = processedBmp.width
                        val finalHeight = processedBmp.height
                        val imageBytes: ByteArray = processedBmp.toBytes()
                        LogContext.log.w(logTag,
                            "Bitmap to bytes cost=${System.currentTimeMillis() - st3}ms")

                        oriBmp.recycledSafety()
                        processedBmp.recycledSafety()

                        LogContext.log.w(logTag, "After recycledSafety()")

                        withContext(Dispatchers.Main) {
                            onImageSaved(CaptureImage.ImageBytes(imageBytes,
                                finalWidth,
                                finalHeight),
                                null)
                        }
                    }
                } catch (e: Exception) {
                    LogContext.log.e(logTag, "Process onCaptureSuccess() error", e)
                    requireActivity().runOnUiThread { onImageSaved(null, e) }
                }
            }

            override fun onError(exc: ImageCaptureException) {
                LogContext.log.e(logTag,
                    "ImageCapturedCallback - Photo capture failed: ${exc.message}",
                    exc)
                requireActivity().runOnUiThread { onImageSaved(null, exc) }
            }
        })
    }

    /**
     * @param onImageSaved It guarantees that if the exc property in this function is not null,
     * the savedImage property must be null.
     * On the contrary, if the savedImage property in this function is not null,
     * the exc property must be null.
     */
    protected fun captureForOutputFile(viewFinder: PreviewView,
        imageCapture: ImageCapture,
        outputDirectory: File,
        startTimestamp: Long,
        onImageSaved: (savedImage: CaptureImage.ImageUri?, exc: Exception?) -> Unit) {
        // Create output file to hold the image
        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

        // Setup image capture metadata
        //        val metadata = ImageCapture.Metadata().apply {
        //            // Mirror image when using the front camera
        //            isReversedHorizontal = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA
        //        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        //        val outputOptions =
        //                ImageCapture.OutputFileOptions.Builder(photoFile).setMetadata(metadata).build()

        val mirror = CameraSelector.DEFAULT_FRONT_CAMERA == lensFacing

        // Setup image capture listener which is triggered after photo has been taken
        imageCapture.takePicture(outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    LogContext.log.e(logTag,
                        "ImageSavedCallback - Photo capture failed: ${exc.message}",
                        exc)
                    requireActivity().runOnUiThread { onImageSaved(null, exc) }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    LogContext.log.w(logTag,
                        "Capture image file cost=${System.currentTimeMillis() - startTimestamp}ms")
                    try {
                        showShutterAnimation(viewFinder)
                        soundManager.playShutterSound()
                        val savedUri: Uri = output.savedUri ?: Uri.fromFile(photoFile)
                        //                val tmpRotation = cameraRotationInDegree - 90
                        //                val imageRotation = if (tmpRotation < 0) 270 else tmpRotation

                        lifecycleScope.launch(Dispatchers.IO) {
                            val st1 = System.currentTimeMillis()
                            val oriBmp: Bitmap = BitmapFactory.decodeFile(savedUri.path)
                            val st2 = System.currentTimeMillis()
                            LogContext.log.w(logTag, "Decode bitmap file cost=${st2 - st1}ms")
                            adjustBitmapRotation(oriBmp,
                                mirror,
                                cameraRotationInDegree).run {
                                writeToFile(photoFile)
                                recycledSafety()
                            }
                            val st3 = System.currentTimeMillis()
                            LogContext.log.w(logTag, "Mirror and rotate cost=${st3 - st2}ms")
                            requireActivity().runOnUiThread {
                                onImageSaved(CaptureImage.ImageUri(savedUri), null)
                            }
                        }
                    } catch (e: Exception) {
                        LogContext.log.e(logTag, "Process onImageSaved() error", e)
                        requireActivity().runOnUiThread { onImageSaved(null, e) }
                    }
                }
            })
    }

    private fun adjustBitmapRotation(bitmap: Bitmap,
        mirror: Boolean,
        cameraSensorOrientationDegree: Int): Bitmap {
        val imageRotationDegree: Int = if (mirror) {
            when (cameraSensorOrientationDegree) {
                0             -> 0
                270           -> 90
                180           -> 180
                else /* 90 */ -> 270
            }
        } else cameraSensorOrientationDegree

        return bitmap.flipRotate(mirror, false, imageRotationDegree.toFloat())
    }

    private fun showShutterAnimation(viewFinder: PreviewView) {
        // We can only change the foreground Drawable using API level 23+ API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            viewFinder.apply {
                post {
                    // Display flash animation to indicate that photo was captured.
                    foreground =
                            ColorDrawable(ResourcesCompat.getColor(resources,
                                R.color.camera_flash_layer,
                                null))
                    postDelayed({ foreground = null }, ANIMATION_SLOW_MILLIS)
                }
            }
        }
    }

    protected suspend fun configCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()
    }

    protected fun initCameraGesture(viewFinder: PreviewView, camera: Camera) {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio: Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1F
                val delta = detector.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                LogContext.log.w(logTag,
                    "currentZoomRatio=$currentZoomRatio delta=$delta New zoomRatio=${currentZoomRatio * delta}")
                return true
            }
        }

        val gestureListener: GestureDetector.SimpleOnGestureListener =
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        LogContext.log.w(logTag, "Double click[${e.x},${e.y}] to zoom.")
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
                        LogContext.log.w(logTag, "Single tap[${e.x},${e.y}] to focus.")
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
                                    LogContext.log.w(logTag, "Focus successfully.")
                                    touchListener?.onFocusSuccess()
                                } else {
                                    LogContext.log.w(logTag, "Focus failed.")
                                    touchListener?.onFocusFail()
                                }
                            }
                        }, ContextCompat.getMainExecutor(requireContext()))
                        return true
                    }

                    // ====================

                    override fun onFling(e1: MotionEvent,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float): Boolean {
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

                        // LogContext.log.v(logTag, "deltaX=$deltaX deltaY=$deltaY")

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

    fun setSwipeCallback(left: () -> Unit = {},
        right: () -> Unit = {},
        up: () -> Unit = {},
        down: () -> Unit = {}) {
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

    protected fun checkForHdrExtensionAvailability(hasHdr: Boolean,
        callback: (isHdrAvailable: Boolean) -> Unit) {
        // Create a Vendor Extension for HDR
        val extensionsManagerFuture =
                ExtensionsManager.getInstanceAsync(requireContext(), cameraProvider ?: return)
        extensionsManagerFuture.addListener({
            val extensionsManager = extensionsManagerFuture.get() ?: return@addListener
            val isAvailable = extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.HDR)

            // Check for any extension availability
            LogContext.log.i(logTag,
                "AUTO: ${extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.AUTO)}")
            LogContext.log.i(logTag,
                "HDR: ${extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.HDR)}")
            LogContext.log.i(logTag,
                "FACE RETOUCH: ${
                    extensionsManager.isExtensionAvailable(lensFacing,
                        ExtensionMode.FACE_RETOUCH)
                }")
            LogContext.log.i(logTag,
                "BOKEH: ${extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.BOKEH)}")
            LogContext.log.i(logTag,
                "NIGHT: ${extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.NIGHT)}")
            LogContext.log.i(logTag,
                "NONE: ${extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.NONE)}")

            // Check if the extension is available on the device
            if (!isAvailable) {
                callback(false)
            } else if (hasHdr) {
                callback(true)
                hdrCameraSelector =
                        extensionsManager.getExtensionEnabledCameraSelector(lensFacing,
                            ExtensionMode.HDR)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
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

    private fun updatePreviewViewLayoutParams(previewView: PreviewView, ratio: CameraRatio) {
        previewView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = 0
            height = 0
            if (ratio != CameraRatio.RFull) dimensionRatio = ratio.ratioString
        }
    }

    protected fun showAvailableRatio(incRatioBinding: IncRatioOptionsBinding,
        ratio: CameraRatio,
        previewView: PreviewView,
        ratioBtn: ImageButton? = null) {
        val metrics = requireContext().getRealResolution()

        val cameraId = if (CameraSelector.DEFAULT_BACK_CAMERA == lensFacing) "0" else "1"
        val characteristics: CameraCharacteristics =
                cameraManager.getCameraCharacteristics(cameraId)

        characteristics.getCameraSupportedSize().forEach {
            when (com.leovp.lib_common_android.exts.getRatio(it)) {
                "16:9" -> {
                    incRatioBinding.btnRatio16v9.visibility = View.VISIBLE
                    return@forEach
                }
                "4:3"  -> {
                    incRatioBinding.btnRatio4v3.visibility = View.VISIBLE
                    return@forEach
                }
                "1:1"  -> {
                    incRatioBinding.btnRatio1v1.visibility = View.VISIBLE
                    return@forEach
                }
            }
            if ((it.long * 1.0 / it.short).round(1) == (metrics.height * 1.0 / metrics.width).round(
                    1)
            ) {
                incRatioBinding.btnRatioFull.visibility = View.VISIBLE
            }
        }

        updateRatioUI(ratio, previewView, ratioBtn)
    }

    protected fun updateRatioUI(ratio: CameraRatio,
        previewView: PreviewView,
        ratioBtn: ImageButton? = null) {
        ratioBtn?.visibility = View.GONE
        when (ratio) {
            CameraRatio.R16v9 -> {
                ratioBtn?.visibility = View.VISIBLE
                ratioBtn?.setImageResource(R.drawable.ic_ratio_16v9)
                updatePreviewViewLayoutParams(previewView, ratio)
                previewView.topMargin = resources.dp2px(64f)
            }
            CameraRatio.R4v3  -> {
                ratioBtn?.visibility = View.VISIBLE
                ratioBtn?.setImageResource(R.drawable.ic_ratio_4v3)
                updatePreviewViewLayoutParams(previewView, ratio)
                previewView.topMargin = resources.dp2px(74f)
            }
            CameraRatio.R1v1  -> {
                ratioBtn?.visibility = View.VISIBLE
                ratioBtn?.setImageResource(R.drawable.ic_ratio_1v1)
                updatePreviewViewLayoutParams(previewView, ratio)
                previewView.topMargin = resources.dp2px(112f)
            }
            CameraRatio.RFull -> {
                ratioBtn?.visibility = View.VISIBLE
                ratioBtn?.setImageResource(R.drawable.ic_ratio_full)
                previewView.run {
                    updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = FrameLayout.LayoutParams.MATCH_PARENT
                        height = FrameLayout.LayoutParams.MATCH_PARENT
                    }
                    topMargin = 0
                }
            }
        }
    }

    protected fun getMaxPreviewSize(camSelector: CameraSelector): Size {
        val screenMetrics = requireContext().getRealResolution()
        return if (cameraProvider?.hasCamera(camSelector) == true) {
            val cameraId = if (CameraSelector.DEFAULT_BACK_CAMERA == camSelector) "0" else "1"
            val characteristics: CameraCharacteristics =
                    cameraManager.getCameraCharacteristics(cameraId)
            getSpecificPreviewOutputSize(requireContext(),
                screenMetrics.width,
                screenMetrics.height,
                characteristics)
        } else {
            Size(0, 0)
        }
    }

    protected fun switchCameraSelector() {
        lensFacing = when {
            hasBackCamera() && hasFrontCamera() -> {
                if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
            }
            hasBackCamera()                     -> CameraSelector.DEFAULT_BACK_CAMERA
            hasFrontCamera()                    -> CameraSelector.DEFAULT_FRONT_CAMERA
            else                                -> {
                LogContext.log.e(logTag, "Error: This device does not have any camera, bailing out")
                //                requireActivity().finish()
                throw RuntimeException("This device does not have any camera")
            }
        }
        saveCameraLensFacing()
        updateOrientationLiveData()
    }

    private fun saveCameraLensFacing() {
        val cameraId = if (CameraSelector.DEFAULT_BACK_CAMERA == lensFacing) "0" else "1"
        prefs.putString(KEY_LENS_FACING, cameraId)
    }

    private fun loadCameraLensFacing(): CameraSelector {
        // Default value: Back camera
        val cameraId = prefs.getString(KEY_LENS_FACING, "0")
        return if ("0" == cameraId) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    protected fun outputCameraParameters(camSelector: CameraSelector/*, desiredVideoWidth: Int, desiredVideoHeight: Int*/) =
            runCatching {
                if (cameraProvider?.hasCamera(camSelector) == true) {
                    val cameraId =
                            if (CameraSelector.DEFAULT_BACK_CAMERA == camSelector) "0" else "1"
                    val characteristics: CameraCharacteristics =
                            cameraManager.getCameraCharacteristics(cameraId)
                    val configMap = characteristics.getConfigMap()

                    val isFlashSupported = characteristics.isFlashSupported()
                    // LEVEL_3(3) > FULL(1) > LIMIT(0) > LEGACY(2)
                    val hardwareLevel = characteristics.hardwareLevel()
                    // Get camera supported fps. It will be used to create CaptureRequest
                    val cameraSupportedFpsRanges: Array<Range<Int>> =
                            characteristics.supportedFpsRanges()
                    val highSpeedVideoFpsRanges = configMap.highSpeedVideoFpsRanges
                    val highSpeedVideoSizes = configMap.highSpeedVideoSizes

                    val allCameraSupportSize = configMap.getOutputSizes(SurfaceHolder::class.java)

                    // Calculate ImageReader input preview size from supported size list by camera.
                    // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
                    // Attention: The returned value is in camera orientation. NOT in device orientation.
                    //                    val previewSize: Size =
                    //                            getSpecificPreviewOutputSize(requireContext(),
                    //                                desiredVideoWidth,
                    //                                desiredVideoHeight,
                    //                                characteristics)

                    val cameraParametersString = """Camera Info:
               cameraId=${if (cameraId == "0") "BACK" else "FRONT"}
         deviceRotation=${requireContext().getDeviceRotation()}
cameraSensorOrientation=${characteristics.cameraSensorOrientation()}
       isFlashSupported=$isFlashSupported
          hardwareLevel=$hardwareLevel[${characteristics.hardwareLevelName()}]

 Supported color format for  AVC=${
                        getSupportedColorFormatForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).sorted()
                            .joinToString(",")
                    }
 Supported color format for HEVC=${
                        getSupportedColorFormatForEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC).sorted()
                            .joinToString(",")
                    }

Supported profile/level for  AVC=${
                        getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).joinToString(
                            ",") { "${it.profile}/${it.level}" }
                    }
Supported profile/level for HEVC=${
                        getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).joinToString(
                            ",") { "${it.profile}/${it.level}" }
                    }

     highSpeedVideoFpsRanges=${highSpeedVideoFpsRanges?.contentToString()}
         highSpeedVideoSizes=${
                        highSpeedVideoSizes?.joinToString(",") {
                            "${it.width}x${it.height}(${
                                getRatio(it.width, it.height)
                            })"
                        }
                    }

        Supported FPS Ranges=${cameraSupportedFpsRanges.contentToString()}
              Supported Size=${
                        allCameraSupportSize?.joinToString(",") {
                            "${it.width}x${it.height}" +
                                    "(${getCameraSizeTotalPixels(it)}-" +
                                    "${getRatio(it.width, it.height)})"
                        }
                    }
        """.trimIndent()
                    LogContext.log.i(logTag, cameraParametersString)
                    LogContext.log.i(logTag, "==================================================")
                }
            }.onFailure { LogContext.log.e(logTag, "outputCameraParameters error.", it) }

    protected fun getMedia(): List<Media> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getMediaQPlus()
    } else {
        getMediaQMinus()
    }.reversed()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getMediaQPlus(): List<Media> {
        val items = mutableListOf<Media>()
        val contentResolver = requireContext().applicationContext.contentResolver

        contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DATE_TAKEN,
            ),
            null,
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)

                val contentUri: Uri =
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                if (path == outputPictureDirectory.absolutePath) {
                    items.add(Media(contentUri, true, date))
                }
            }
        }

        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_TAKEN,
            ),
            null,
            null,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)

                val contentUri: Uri =
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                if (path == outputPictureDirectory.absolutePath) {
                    items.add(Media(contentUri, false, date))
                }
            }
        }
        return items
    }

    private fun getMediaQMinus(): List<Media> {
        val items = mutableListOf<Media>()

        outputPictureDirectory.listFiles()?.forEach {
            val authority = requireContext().applicationContext.packageName + ".provider"
            val mediaUri = FileProvider.getUriForFile(requireContext(), authority, it)
            items.add(Media(mediaUri, it.extension == "mp4", it.lastModified()))
        }

        return items
    }

    companion object {
        internal const val FILENAME = "yyyyMMdd_HHmmss_SSS"

        internal const val BASE_FOLDER_NAME = "CameraX"

        internal const val KEY_FLASH = "camerax-flash"
        internal const val KEY_GRID = "camerax-grid"
        internal const val KEY_HDR = "camerax-hdr"
        internal const val KEY_LENS_FACING = "camerax-lens-facing"

        internal const val MIN_SWIPE_DISTANCE = 300

        /** Milliseconds used for UI animations */
        internal const val ANIMATION_SLOW_MILLIS = 100L

        internal const val PHOTO_EXTENSION = ".jpg"
        internal const val VIDEO_EXTENSION = ".mp4"

        /** Helper function used to create a timestamped file */
        internal fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder,
                    SimpleDateFormat(format,
                        Locale.US).format(System.currentTimeMillis()) + extension)

        internal fun getOutputPictureDirectory(context: Context,
            parentFolder: String = BASE_FOLDER_NAME): File {
            return File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                parentFolder).also {
                if (!it.exists()) it.mkdirs()
            }
        }

        internal fun getOutputVideoDirectory(context: Context,
            parentFolder: String = BASE_FOLDER_NAME): File {
            return File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                parentFolder).also {
                if (!it.exists()) it.mkdirs()
            }
        }
    }
}