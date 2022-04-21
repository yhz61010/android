package com.leovp.camerax_sdk.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import coil.load
import coil.transform.CircleCropTransformation
import com.hjq.permissions.XXPermissions
import com.leovp.camerax_sdk.R
import com.leovp.camerax_sdk.analyzer.LuminosityAnalyzer
import com.leovp.camerax_sdk.databinding.CameraUiContainerBottomBinding
import com.leovp.camerax_sdk.databinding.CameraUiContainerTopBinding
import com.leovp.camerax_sdk.databinding.FragmentCameraBinding
import com.leovp.camerax_sdk.enums.CameraTimer
import com.leovp.camerax_sdk.utils.SharedPrefsManager
import com.leovp.camerax_sdk.utils.toggleButton
import com.leovp.lib_common_android.exts.*
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment() {
    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.getInstance(requireContext()) }
    private val audioManager: AudioManager by lazy { requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private var _cameraUiContainerTopBinding: CameraUiContainerTopBinding? = null
    private var _cameraUiContainerBottomBinding: CameraUiContainerBottomBinding? = null
    private val cameraUiContainerTopBinding get() = _cameraUiContainerTopBinding!!
    private val cameraUiContainerBottomBinding get() = _cameraUiContainerBottomBinding!!

    // Selector showing is grid enabled or not
    private var hasGrid = false

    // Selector showing is hdr enabled or not (will work, only if device's camera supports hdr on hardware level)
    private var hasHdr = false

    // Selector showing is there any selected timer and it's value (3s or 10s)
    private var selectedTimer = CameraTimer.OFF

    // Selector showing which flash mode is selected (on, off or auto)
    private var flashMode by Delegates.observable(ImageCapture.FLASH_MODE_OFF) { _, _, new ->
        _cameraUiContainerTopBinding?.btnFlash?.setImageResource(
            when (new) {
                ImageCapture.FLASH_MODE_ON   -> R.drawable.ic_flash_on
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                else                         -> R.drawable.ic_flash_off
            }
        )
    }

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var outputDirectory: File
    val functionKey: MutableLiveData<Int> by lazy { MutableLiveData<Int>() }
    private val functionKeyObserver = Observer<Int> { keyCode ->
        when (keyCode) {
            // When the volume up/down button is pressed, simulate a shutter button click
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> cameraUiContainerBottomBinding.cameraCaptureButton.simulateClick()
            KeyEvent.KEYCODE_UNKNOWN     -> Unit
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                LogContext.log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    private var soundIdCountdown: Int = 0
    private lateinit var soundPool: SoundPool

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!XXPermissions.isGranted(requireContext(), PermissionsFragment.PERMISSIONS_REQUIRED)) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        runCatching {
            soundPool.autoPause()
            soundPool.release()
        }
        super.onDestroyView()
        // Shut down our background executor
        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        functionKey.observe(viewLifecycleOwner, functionKeyObserver)
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    private fun setGalleryThumbnail(uri: Uri) {
        // Run the operations in the view's thread
        cameraUiContainerBottomBinding.photoViewButton.let { photoViewButton ->
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

    private fun loadSounds() = lifecycleScope.launch(Dispatchers.IO) {
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
            ).build().apply {
                val countdownSoundId = load(requireContext(), R.raw.countdown, 1)
                soundIdCountdown = countdownSoundId
                load(requireContext(), R.raw.countdown, 1)
            }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPrefs()
        loadSounds()

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = getOutputDirectory(requireContext())

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = fragmentCameraBinding.viewFinder.display.displayId
            // Build UI controls
            updateCameraUi()
            // Set up the camera and its use cases
            setUpCamera()
        }
    }

    private fun loadPrefs() {
        flashMode = prefs.getInt(KEY_FLASH, ImageCapture.FLASH_MODE_OFF)
        hasGrid = prefs.getBoolean(KEY_GRID, false)
        hasHdr = prefs.getBoolean(KEY_HDR, false)
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        bindCameraUseCases()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera()  -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else             -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = requireContext().getRealResolution()
        LogContext.log.i(TAG, "Screen metrics: ${metrics.width} x ${metrics.height}")

        val screenAspectRatio = aspectRatio(metrics.width, metrics.height)
        LogContext.log.i(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = fragmentCameraBinding.viewFinder.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            // Set capture flash
            .setFlashMode(flashMode)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        checkForHdrExtensionAvailability()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                    // Values returned from our analyzer are passed to the attached listener
                    // We log image analysis results here - you should do something useful
                    // instead!
                    LogContext.log.v(TAG, "Average luminosity: $luma")
                })
            }

        try {
            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this,
                cameraSelector, preview, imageCapture, imageAnalyzer).apply {
                // Init camera exposure control
                cameraInfo.exposureState.run {
                    val lower = exposureCompensationRange.lower
                    val upper = exposureCompensationRange.upper

                    cameraUiContainerTopBinding.sliderExposure.run {
                        valueFrom = lower.toFloat()
                        valueTo = upper.toFloat()
                        stepSize = 1f
                        value = exposureCompensationIndex.toFloat()

                        addOnChangeListener { _, value, _ ->
                            cameraControl.setExposureCompensationIndex(value.toInt())
                        }
                    }
                }
            }

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            observeCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception) {
            LogContext.log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun checkForHdrExtensionAvailability() {
        // TODO("Not yet implemented")
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    // Ask the user to close other camera apps
                    CameraState.Type.PENDING_OPEN -> Toast.makeText(context, "CameraState: Pending Open", Toast.LENGTH_SHORT).show()
                    // Show the Camera UI
                    CameraState.Type.OPENING      -> Toast.makeText(context, "CameraState: Opening", Toast.LENGTH_SHORT).show()
                    // Setup Camera resources and begin processing
                    CameraState.Type.OPEN         -> Toast.makeText(context, "CameraState: Open", Toast.LENGTH_SHORT).show()
                    // Close camera UI
                    CameraState.Type.CLOSING      -> Toast.makeText(context, "CameraState: Closing", Toast.LENGTH_SHORT).show()
                    // Free camera resources
                    CameraState.Type.CLOSED       -> Toast.makeText(context, "CameraState: Closed", Toast.LENGTH_SHORT).show()
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    // Make sure to setup the use cases properly
                    CameraState.ERROR_STREAM_CONFIG               -> Toast.makeText(context, "Stream config error", Toast.LENGTH_SHORT).show()
                    // Opening errors
                    // Close the camera or ask user to close another camera app that's using the camera
                    CameraState.ERROR_CAMERA_IN_USE               -> Toast.makeText(context, "Camera in use", Toast.LENGTH_SHORT).show()
                    // Close another open camera in the app,
                    // or ask the user to close another camera app that's using the camera
                    CameraState.ERROR_MAX_CAMERAS_IN_USE          -> Toast.makeText(context, "Max cameras in use", Toast.LENGTH_SHORT).show()
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR     -> Toast.makeText(context, "Other recoverable error", Toast.LENGTH_SHORT).show()
                    // Closing errors
                    // Ask the user to enable the device's cameras
                    CameraState.ERROR_CAMERA_DISABLED             -> Toast.makeText(context, "Camera disabled", Toast.LENGTH_SHORT).show()
                    // Ask the user to reboot the device to restore camera function
                    CameraState.ERROR_CAMERA_FATAL_ERROR          -> Toast.makeText(context, "Fatal error", Toast.LENGTH_SHORT).show()
                    // Closed errors
                    // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> Toast.makeText(context, "Do not disturb mode enabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        // Remove previous UI if any
        _cameraUiContainerTopBinding?.root?.let { fragmentCameraBinding.root.removeView(it) }
        _cameraUiContainerBottomBinding?.root?.let { fragmentCameraBinding.root.removeView(it) }

        _cameraUiContainerTopBinding = CameraUiContainerTopBinding.inflate(
            requireContext().layoutInflater,
            fragmentCameraBinding.root,
            true
        )
        _cameraUiContainerBottomBinding = CameraUiContainerBottomBinding.inflate(
            requireContext().layoutInflater,
            fragmentCameraBinding.root,
            true
        )

        // --------------------

        cameraUiContainerTopBinding.btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
        cameraUiContainerTopBinding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE
        adjustInsets()

        cameraUiContainerTopBinding.btnGrid.setOnSingleClickListener { toggleGrid() }
        cameraUiContainerTopBinding.btnFlash.setOnClickListener { showFlashLayer() }
        cameraUiContainerTopBinding.btnFlashOff.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_OFF) }
        cameraUiContainerTopBinding.btnFlashOn.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_ON) }
        cameraUiContainerTopBinding.btnFlashAuto.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_AUTO) }
        cameraUiContainerTopBinding.btnTimer.setOnClickListener { showSelectTimerLayer() }
        cameraUiContainerTopBinding.btnTimerOff.setOnClickListener { closeTimerAndSelect(CameraTimer.OFF) }
        cameraUiContainerTopBinding.btnTimer3.setOnClickListener { closeTimerAndSelect(CameraTimer.S3) }
        cameraUiContainerTopBinding.btnTimer10.setOnClickListener { closeTimerAndSelect(CameraTimer.S10) }
        cameraUiContainerTopBinding.btnExposure.setOnClickListener { cameraUiContainerTopBinding.flExposure.visibility = View.VISIBLE }
        cameraUiContainerTopBinding.flExposure.setOnClickListener { cameraUiContainerTopBinding.flExposure.visibility = View.GONE }

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.uppercase(Locale.ROOT))
            }?.maxOrNull()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }

        // Listener for button used to capture photo
        cameraUiContainerBottomBinding.cameraCaptureButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                startCountdown()
                captureImage()
            }
        }

        // Setup for button used to switch cameras
        cameraUiContainerBottomBinding.cameraSwitchButton.let {
            // Disable the button until the camera is set up
            it.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
        }

        // Listener for button used to view the most recent photo
        cameraUiContainerBottomBinding.photoViewButton.setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax
                ).navigate(CameraFragmentDirections.actionCameraToGallery(outputDirectory.absolutePath))
            }
        }
    }

    private fun captureImage() {
        // Get a stable reference of the modifiable image capture use case
        imageCapture?.let { imageCapture ->
            // Create output file to hold the image
            val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

            // Setup image capture metadata
            val metadata = Metadata().apply {
                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

            //                imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            //                    override fun onCaptureSuccess(image: ImageProxy) {
            //                        val imageBuffer = image.planes[0].buffer
            //                        val width = image.width
            //                        val height = image.height
            //                        val imageBytes = ByteArray(imageBuffer.remaining()).apply { imageBuffer.get(this) }
            //                        // DO NOT forget for close Image object
            //                        image.close()
            //                    }
            //
            //                    override fun onError(exc: ImageCaptureException) {
            //                        LogContext.log.e(TAG, "ImageCapturedCallback - Photo capture failed: ${exc.message}", exc)
            //                    }
            //                })

            // Setup image capture listener which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        LogContext.log.e(TAG, "ImageSavedCallback - Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                        LogContext.log.i(TAG, "Photo capture succeeded: $savedUri")

                        // We can only change the foreground Drawable using API level 23+ API
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Update the gallery thumbnail with latest picture taken
                            setGalleryThumbnail(savedUri)
                        }

                        // Implicit broadcasts will be ignored for devices running API level >= 24
                        // so if you only target API level 24+ you can remove this statement
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                            @Suppress("DEPRECATION")
                            requireActivity().sendBroadcast(Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri))
                        }

                        // If the folder selected is an external media directory, this is
                        // unnecessary but otherwise other apps will not be able to access our
                        // images unless we scan them using [MediaScannerConnection]
                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(savedUri.toFile().extension)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(savedUri.toFile().absolutePath),
                            arrayOf(mimeType)
                        ) { _, uri ->
                            LogContext.log.i(TAG, "Image capture scanned into media store: $uri")
                        }
                    }
                })

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Display flash animation to indicate that photo was captured
                fragmentCameraBinding.root.postDelayed({
                    fragmentCameraBinding.root.foreground = ColorDrawable(Color.parseColor("#99DDDDDD"))
                    fragmentCameraBinding.root.postDelayed({ fragmentCameraBinding.root.foreground = null }, ANIMATION_FAST_MILLIS)
                }, ANIMATION_SLOW_MILLIS)
            }
        }
    }

    private fun playCountdownSound(volume: Float) {
        soundPool.play(soundIdCountdown, volume, volume, 1, 0, 1f)
    }

    private suspend fun startCountdown() = coroutineScope {
        if (CameraTimer.OFF != selectedTimer) {
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            playCountdownSound(volume)
        }
        // Show a timer based on user selection
        when (selectedTimer) {
            CameraTimer.S3  -> for (i in CameraTimer.S3.delay downTo 1) {
                cameraUiContainerTopBinding.tvCountDown.text = i.toString()
                delay(1000)
            }
            CameraTimer.S10 -> for (i in CameraTimer.S10.delay downTo 1) {
                cameraUiContainerTopBinding.tvCountDown.text = i.toString()
                delay(1000)
            }
            else            -> Unit
        }
        cameraUiContainerTopBinding.tvCountDown.text = ""
    }

    /**
     * This methods adds all necessary margins to some views based on window insets and screen orientation
     */
    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        cameraUiContainerBottomBinding.cameraCaptureButton.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.bottomMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            }
        }
        cameraUiContainerTopBinding.btnTimer.onWindowInsets { view, windowInsets ->
            view.topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
        cameraUiContainerTopBinding.llTimerOptions.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.topPadding = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            } else {
                view.startPadding = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left
            }
        }
        cameraUiContainerTopBinding.llFlashOptions.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.topPadding = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            } else {
                view.startPadding = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left
            }
        }
    }

    /**
     * Show timer selection menu by circular reveal animation.
     * circularReveal() function is an Extension function which is adding the circular reveal
     */
    private fun showSelectTimerLayer() = cameraUiContainerTopBinding.llTimerOptions.circularReveal(cameraUiContainerTopBinding.btnTimer)

    /**
     * Show flashlight selection menu by circular reveal animation.
     * circularReveal() function is an Extension function which is adding the circular reveal
     */
    private fun showFlashLayer() = cameraUiContainerTopBinding.llFlashOptions.circularReveal(cameraUiContainerTopBinding.btnFlash)

    /** Turns on or off the grid on the screen */
    private fun toggleGrid() {
        LogContext.log.i(TAG, "toggleGrid currentGridFlag=$hasGrid")
        cameraUiContainerTopBinding.btnGrid.toggleButton(
            flag = hasGrid,
            rotationAngle = 180f,
            firstIcon = R.drawable.ic_grid_off,
            secondIcon = R.drawable.ic_grid_on,
        ) { flag ->
            hasGrid = flag
            prefs.putBoolean(KEY_GRID, flag)
            cameraUiContainerTopBinding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
        }
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        try {
            cameraUiContainerBottomBinding.cameraSwitchButton.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            cameraUiContainerBottomBinding.cameraSwitchButton.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean = cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean = cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false

    /**
     * This function is called from XML view via Data Binding to select a timer
     *  possible values are OFF, S3 or S10
     *  circularClose() function is an Extension function which is adding circular close
     */
    private fun closeTimerAndSelect(timer: CameraTimer) {
        cameraUiContainerTopBinding.llTimerOptions.circularClose(cameraUiContainerTopBinding.btnTimer) {
            selectedTimer = timer
            cameraUiContainerTopBinding.btnTimer.setImageResource(
                when (timer) {
                    CameraTimer.S3  -> R.drawable.ic_timer_3
                    CameraTimer.S10 -> R.drawable.ic_timer_10
                    CameraTimer.OFF -> R.drawable.ic_timer_off
                }
            )
        }
    }

    /**
     * This function is called from XML view via Data Binding to select a FlashMode
     *  possible values are ON, OFF or AUTO
     *  circularClose() function is an Extension function which is adding circular close
     */
    private fun closeFlashAndSelect(@ImageCapture.FlashMode flash: Int) {
        cameraUiContainerTopBinding.llFlashOptions.circularClose(cameraUiContainerTopBinding.btnFlash) {
            flashMode = flash
            cameraUiContainerTopBinding.btnFlash.setImageResource(
                when (flash) {
                    ImageCapture.FLASH_MODE_ON  -> R.drawable.ic_flash_on
                    ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                    else                        -> R.drawable.ic_flash_auto
                }
            )
            imageCapture?.flashMode = flashMode
            prefs.putInt(KEY_FLASH, flashMode)
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyyMMdd-HHmmss.SSS"

        private const val KEY_FLASH = "camerax-flash"
        private const val KEY_GRID = "camerax-grid"
        private const val KEY_HDR = "camerax-hdr"

        /** Milliseconds used for UI animations */
        private const val ANIMATION_FAST_MILLIS = 50L
        private const val ANIMATION_SLOW_MILLIS = 100L
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File,
            @Suppress("SameParameterValue") format: String,
            @Suppress("SameParameterValue") extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension)

        private fun getOutputDirectory(context: Context): File {
            return File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CameraX").also {
                if (!it.exists()) it.mkdirs()
            }
        }
    }
}