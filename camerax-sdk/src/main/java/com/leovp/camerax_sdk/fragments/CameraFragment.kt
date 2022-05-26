package com.leovp.camerax_sdk.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toFile
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.hjq.permissions.XXPermissions
import com.leovp.camerax_sdk.R
import com.leovp.camerax_sdk.analyzer.LuminosityAnalyzer
import com.leovp.camerax_sdk.databinding.CameraUiContainerBottomBinding
import com.leovp.camerax_sdk.databinding.CameraUiContainerTopBinding
import com.leovp.camerax_sdk.databinding.FragmentCameraBinding
import com.leovp.camerax_sdk.enums.CameraRatio
import com.leovp.camerax_sdk.enums.CameraTimer
import com.leovp.camerax_sdk.fragments.base.BaseCameraXFragment
import com.leovp.camerax_sdk.listeners.CameraXTouchListener
import com.leovp.camerax_sdk.listeners.CaptureImageListener
import com.leovp.camerax_sdk.utils.SharedPrefsManager
import com.leovp.camerax_sdk.utils.toggleButton
import com.leovp.lib_common_android.exts.*
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.properties.Delegates
import kotlin.system.measureTimeMillis

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : BaseCameraXFragment<FragmentCameraBinding>() {
    override fun getTagName() = "CameraFragment"

    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.getInstance(requireContext()) }

    override fun getViewBinding(inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): FragmentCameraBinding {
        return FragmentCameraBinding.inflate(inflater, container, false)
    }

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax)
    }

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var _cameraUiContainerTopBinding: CameraUiContainerTopBinding? = null
    private var _cameraUiContainerBottomBinding: CameraUiContainerBottomBinding? = null
    private val cameraUiContainerTopBinding get() = _cameraUiContainerTopBinding!!
    private val cameraUiContainerBottomBinding get() = _cameraUiContainerBottomBinding!!

    var allowToOutputCaptureFile: Boolean = true
    var captureImageListener: CaptureImageListener? = null

    // Selector showing is grid enabled or not
    private var hasGrid = false

    // Selector showing is hdr enabled or not (will work, only if device's camera supports hdr on hardware level)
    private var enableHdr = false

    // Selector showing is there any selected timer and it's value (3s or 10s)
    private var selectedTimer = CameraTimer.OFF

    private var selectedRatio = CameraRatio.R4v3

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
                LogContext.log.d(logTag, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!XXPermissions.isGranted(requireContext(), PermissionsFragment.PERMISSIONS_REQUIRED)) {
            navController.navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        functionKey.observe(viewLifecycleOwner, functionKeyObserver)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)
        loadPrefs()

        // Wait for the views to be properly laid out
        binding.viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = binding.viewFinder.display.displayId
            // Build UI controls
            updateCameraUi()
            lifecycleScope.launch(Dispatchers.Main) {
                // Set up the camera and its use cases
                setUpCamera()
            }
        }
    }

    override fun onDestroyView() {
        displayManager.unregisterDisplayListener(displayListener)
        super.onDestroyView()
    }

    private fun loadPrefs() {
        flashMode = prefs.getInt(KEY_FLASH, ImageCapture.FLASH_MODE_OFF)
        hasGrid = prefs.getBoolean(KEY_GRID, false)
        enableHdr = prefs.getBoolean(KEY_HDR, false)
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
    private suspend fun setUpCamera() {
        configCamera()
        touchListener = object : CameraXTouchListener {
            override fun onStartFocusing(x: Float, y: Float) =
                    cameraUiContainerTopBinding.focusView.startFocus(x.toInt(), y.toInt())

            override fun onFocusSuccess() = cameraUiContainerTopBinding.focusView.focusSuccess()

            override fun onFocusFail() = cameraUiContainerTopBinding.focusView.focusFail()

            override fun onDoubleTap(x: Float, y: Float) {
            }

            override fun onZoom(ratio: Float) {
            }

            override fun onScale(scale: Float) {
            }
        }

        // Enable or disable switching between cameras
        updateCameraSwitchButton()

        // Build and bind the camera use cases
        bindCameraUseCases()
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {
        val rotation = binding.viewFinder.display.rotation

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = requireContext().getRealResolution()
        val screenAspectRatio = aspectRatio(metrics.width, metrics.height)
        LogContext.log.w(logTag,
            "Screen metrics: ${metrics.width}x${metrics.height} | Preview AspectRatio: $screenAspectRatio | rotation=$rotation")
        binding.viewFinder.updateLayoutParams<ConstraintLayout.LayoutParams> {
            dimensionRatio = if (screenAspectRatio == AspectRatio.RATIO_16_9) "9:16" else "3:4"
        }

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()
            .apply {
                // Attach the viewfinder's surface provider to preview use case
                setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

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

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            // In our analysis, we care about the latest image
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                    // Values returned from our analyzer are passed to the attached listener
                    // We log image analysis results here - you should do something useful
                    // instead!
                    LogContext.log.v(logTag, "Average luminosity: $luma")
                })
            }

        checkForHdrExtensionAvailability(enableHdr) { isHdrAvailable ->
            if (isHdrAvailable) {
                // If yes, turn on if the HDR is turned on by the user
                cameraUiContainerTopBinding.btnHdr.visibility = View.VISIBLE
            } else {
                // If not, hide the HDR button
                cameraUiContainerTopBinding.btnHdr.visibility = View.GONE
            }
        }

        // CameraProvider
        val camProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed. Did you call configCamera() method?")
        try {
            val outputCameraParamCost = measureTimeMillis {
                val screenDimen = requireContext().getRealResolution()
                outputCameraParameters(hdrCameraSelector ?: lensFacing, screenDimen.width, screenDimen.height)
            }
            LogContext.log.i(logTag, "Output camera parameters cost ${outputCameraParamCost}ms")
            // Must unbind the use-cases before rebinding them
            camProvider.unbindAll()

            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = camProvider.bindToLifecycle(this,
                hdrCameraSelector ?: lensFacing, preview, imageCapture, imageAnalyzer).apply {
                // Init camera exposure control
                cameraInfo.exposureState.run {
                    val lower: Float = exposureCompensationRange.lower.toFloat()
                    val upper: Float = exposureCompensationRange.upper.toFloat()
                    cameraUiContainerTopBinding.sliderExposure.run {
                        LogContext.log.w(logTag, "Exposure[${lower}, $upper]=$exposureCompensationIndex")
                        valueFrom = lower / 10f
                        valueTo = upper / 10f
                        stepSize = 1f / 10
                        value = exposureCompensationIndex / 10f
                        addOnChangeListener { _, value, _ ->
                            LogContext.log.i(logTag, "Exposure change to ${value * 10}")
                            cameraControl.setExposureCompensationIndex((value * 10).toInt())
                        }
                    }
                }
            }

            val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
            val cameraName = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) "Back" else "Front"
            LogContext.log.w(logTag, "$cameraName camera support flash: $hasFlash")
            if (!hasFlash) {
                cameraUiContainerTopBinding.llFlashOptions.circularClose(cameraUiContainerTopBinding.btnFlash)
                cameraUiContainerTopBinding.btnFlash.visibility = View.GONE
            } else {
                cameraUiContainerTopBinding.btnFlash.visibility = View.VISIBLE
            }

            //            observeCameraState(camera?.cameraInfo!!)
            // Call this after [camProvider.bindToLifecycle]
            initCameraGesture(binding.viewFinder, camera!!)
        } catch (exc: Exception) {
            LogContext.log.e(logTag, "Use case binding failed", exc)
        }
    }

    //    private fun observeCameraState(cameraInfo: CameraInfo) {
    //        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
    //            run {
    //                when (cameraState.type) {
    //                    // Ask the user to close other camera apps
    //                    CameraState.Type.PENDING_OPEN -> Toast.makeText(context, "CameraState: Pending Open", Toast.LENGTH_SHORT).show()
    //                    // Show the Camera UI
    //                    CameraState.Type.OPENING      -> Toast.makeText(context, "CameraState: Opening", Toast.LENGTH_SHORT).show()
    //                    // Setup Camera resources and begin processing
    //                    CameraState.Type.OPEN         -> Toast.makeText(context, "CameraState: Open", Toast.LENGTH_SHORT).show()
    //                    // Close camera UI
    //                    CameraState.Type.CLOSING      -> Toast.makeText(context, "CameraState: Closing", Toast.LENGTH_SHORT).show()
    //                    // Free camera resources
    //                    CameraState.Type.CLOSED       -> Toast.makeText(context, "CameraState: Closed", Toast.LENGTH_SHORT).show()
    //                }
    //            }
    //
    //            cameraState.error?.let { error ->
    //                when (error.code) {
    //                    // Open errors
    //                    // Make sure to setup the use cases properly
    //                    CameraState.ERROR_STREAM_CONFIG               -> Toast.makeText(context, "Stream config error", Toast.LENGTH_SHORT).show()
    //                    // Opening errors
    //                    // Close the camera or ask user to close another camera app that's using the camera
    //                    CameraState.ERROR_CAMERA_IN_USE               -> Toast.makeText(context, "Camera in use", Toast.LENGTH_SHORT).show()
    //                    // Close another open camera in the app,
    //                    // or ask the user to close another camera app that's using the camera
    //                    CameraState.ERROR_MAX_CAMERAS_IN_USE          -> Toast.makeText(context, "Max cameras in use", Toast.LENGTH_SHORT).show()
    //                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR     -> Toast.makeText(context, "Other recoverable error", Toast.LENGTH_SHORT).show()
    //                    // Closing errors
    //                    // Ask the user to enable the device's cameras
    //                    CameraState.ERROR_CAMERA_DISABLED             -> Toast.makeText(context, "Camera disabled", Toast.LENGTH_SHORT).show()
    //                    // Ask the user to reboot the device to restore camera function
    //                    CameraState.ERROR_CAMERA_FATAL_ERROR          -> Toast.makeText(context, "Fatal error", Toast.LENGTH_SHORT).show()
    //                    // Closed errors
    //                    // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
    //                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> Toast.makeText(context, "Do not disturb mode enabled", Toast.LENGTH_SHORT).show()
    //                }
    //            }
    //        }
    //    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        setSwipeCallback(
            left = { navController.navigate(R.id.action_camera_fragment_to_video_fragment) },
            right = { navController.navigate(R.id.action_camera_fragment_to_video_fragment) },
            up = { cameraUiContainerBottomBinding.cameraSwitchButton.performClick() },
            down = { cameraUiContainerBottomBinding.cameraSwitchButton.performClick() }
        )

        // Remove previous UI if any
        _cameraUiContainerTopBinding?.root?.let { binding.root.removeView(it) }
        _cameraUiContainerBottomBinding?.root?.let { binding.root.removeView(it) }

        _cameraUiContainerTopBinding = CameraUiContainerTopBinding.inflate(
            requireContext().layoutInflater,
            binding.root,
            true
        )
        _cameraUiContainerBottomBinding = CameraUiContainerBottomBinding.inflate(
            requireContext().layoutInflater,
            binding.root,
            true
        )

        // --------------------

        with(cameraUiContainerTopBinding) {
            btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
            btnGrid.setOnSingleClickListener { toggleGrid() }
            binding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE
            btnFlash.setOnClickListener { showFlashLayer() }
            btnFlashOff.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_OFF) }
            btnFlashOn.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_ON) }
            btnFlashAuto.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_AUTO) }
            btnRatio.setOnClickListener { showRatioLayer() }
            btnRatio4v3.setOnClickListener { closeRatioAndSelect(CameraRatio.R4v3) }
            btnRatio16v9.setOnClickListener { closeRatioAndSelect(CameraRatio.R16v9) }
            btnRatio1v1.setOnClickListener { closeRatioAndSelect(CameraRatio.R1v1) }
            btnRatioFull.setOnClickListener { closeRatioAndSelect(CameraRatio.RFull) }
            btnTimer.setOnClickListener { showSelectTimerLayer() }
            btnTimerOff.setOnClickListener { closeTimerAndSelect(CameraTimer.OFF) }
            btnTimer3.setOnClickListener { closeTimerAndSelect(CameraTimer.S3) }
            btnTimer10.setOnClickListener { closeTimerAndSelect(CameraTimer.S10) }
            btnExposure.setOnClickListener { flExposure.visibility = View.VISIBLE }
            flExposure.setOnClickListener { flExposure.visibility = View.GONE }
        }

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.uppercase(Locale.ROOT))
            }?.maxOrNull()?.let {
                setGalleryThumbnail(Uri.fromFile(it), cameraUiContainerBottomBinding.photoViewButton)
            }
        }

        // Listener for button used to capture photo
        cameraUiContainerBottomBinding.cameraCaptureButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                startCountdown()

                // Get a stable reference of the modifiable image capture use case
                imageCapture?.let { imageCapture ->
                    if (allowToOutputCaptureFile) {
                        captureForOutputFile(imageCapture, outputDirectory) { savedUri ->
                            //                            LogContext.log.i(logTag, "Photo capture succeeded: $savedUri")

                            // We can only change the foreground Drawable using API level 23+ API
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                binding.viewFinder.apply {
                                    post {
                                        // Display flash animation to indicate that photo was captured.
                                        foreground = ColorDrawable(ResourcesCompat.getColor(resources,
                                            R.color.camera_flash_layer, null))
                                        postDelayed({ foreground = null }, ANIMATION_SLOW_MILLIS)
                                    }
                                }
                            }

                            // We can only change the foreground Drawable using API level 23+ API
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                // Update the gallery thumbnail with latest picture taken
                                setGalleryThumbnail(savedUri, cameraUiContainerBottomBinding.photoViewButton)
                            }

                            // Implicit broadcasts will be ignored for devices running API level >= 24
                            // so if you only target API level 24+ you can remove this statement
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                @Suppress("DEPRECATION")
                                // ACTION_NEW_PICTURE = "android.hardware.action.NEW_PICTURE"
                                requireActivity().sendBroadcast(Intent("android.hardware.action.NEW_PICTURE", savedUri))
                            }

                            // If the folder selected is an external media directory, this is
                            // unnecessary but otherwise other apps will not be able to access our
                            // images unless we scan them using [MediaScannerConnection]
                            val mimeType =
                                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(savedUri.toFile().extension)
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(savedUri.toFile().absolutePath),
                                arrayOf(mimeType)
                            ) { path, uri ->
                                LogContext.log.i(logTag, "Image capture scanned into media store: [$uri] [$path]")
                            }

                            captureImageListener?.onSavedImageUri(savedUri)
                        }
                    } else {
                        captureForBytes(imageCapture) { (imageBytes, width, height) ->
                            LogContext.log.i(logTag, "Saved image bytes[${imageBytes.size}] $width x $height")
                            captureImageListener?.onSavedImageBytes(imageBytes, width, height)
                        }
                    }
                }
            }
        }

        // Setup for button used to switch cameras
        cameraUiContainerBottomBinding.cameraSwitchButton.let { switchBtn ->
            // Disable the button until the camera is set up
            switchBtn.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            switchBtn.setOnClickListener {
                it.isEnabled = false
                switchBtn.animate().rotationBy(-180f).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        Handler(Looper.getMainLooper()).postDelayed({ it.isEnabled = true }, 500)
                    }
                })
                lensFacing = switchAndGetCameraSelector()
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
        }

        // Listener for button used to view the most recent photo
        cameraUiContainerBottomBinding.photoViewButton.setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                navController.navigate(CameraFragmentDirections.actionCameraToGallery(outputDirectory.absolutePath))
            }
        }
    }

    private fun showRatioLayer() =
            cameraUiContainerTopBinding.llRatioOptions.circularReveal(cameraUiContainerTopBinding.btnRatio)

    private suspend fun startCountdown() = withContext(Dispatchers.Main) {
        // if (CameraTimer.OFF != selectedTimer) playSound(soundIdCountdown1, getSoundVolume())
        // Show a timer based on user selection
        when (selectedTimer) {
            CameraTimer.S3  -> for (i in CameraTimer.S3.delay downTo 1) {
                soundManager.playTimerSound(i)
                cameraUiContainerTopBinding.tvCountDown.text = i.toString()
                delay(1000)
            }
            CameraTimer.S10 -> for (i in CameraTimer.S10.delay downTo 1) {
                soundManager.playTimerSound(i)
                cameraUiContainerTopBinding.tvCountDown.text = i.toString()
                delay(1000)
            }
            else            -> Unit
        }
        cameraUiContainerTopBinding.tvCountDown.text = ""
    }

    /**
     * Show timer selection menu by circular reveal animation.
     * circularReveal() function is an Extension function which is adding the circular reveal
     */
    private fun showSelectTimerLayer() =
            cameraUiContainerTopBinding.llTimerOptions.circularReveal(cameraUiContainerTopBinding.btnTimer)

    /**
     * Show flashlight selection menu by circular reveal animation.
     * circularReveal() function is an Extension function which is adding the circular reveal
     */
    private fun showFlashLayer() =
            cameraUiContainerTopBinding.llFlashOptions.circularReveal(cameraUiContainerTopBinding.btnFlash)

    /** Turns on or off the grid on the screen */
    private fun toggleGrid() {
        LogContext.log.i(logTag, "toggleGrid currentGridFlag=$hasGrid")
        cameraUiContainerTopBinding.btnGrid.toggleButton(
            flag = hasGrid,
            rotationAngle = 180f,
            firstIcon = R.drawable.ic_grid_off,
            secondIcon = R.drawable.ic_grid_on,
        ) { flag ->
            hasGrid = flag
            prefs.putBoolean(KEY_GRID, flag)
            binding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
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

    /**
     * This function is called from XML view via Data Binding to select a timer
     * possible values are OFF, S3 or S10
     * circularClose() function is an Extension function which is adding circular close
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
     * possible values are ON, OFF or AUTO
     * circularClose() function is an Extension function which is adding circular close
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

    private fun closeRatioAndSelect(ratio: CameraRatio) {
        cameraUiContainerTopBinding.llRatioOptions.circularClose(cameraUiContainerTopBinding.btnRatio) {
            selectedRatio = ratio
            cameraUiContainerTopBinding.btnRatio.setImageResource(
                when (ratio) {
                    CameraRatio.R4v3  -> R.drawable.ic_ratio_4v3
                    CameraRatio.R16v9 -> R.drawable.ic_ratio_16v9
                    CameraRatio.R1v1  -> R.drawable.ic_ratio_1v1
                    CameraRatio.RFull -> R.drawable.ic_ratio_full
                }
            )
        }
    }
}