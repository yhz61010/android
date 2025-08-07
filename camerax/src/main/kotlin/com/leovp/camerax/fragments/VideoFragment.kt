package com.leovp.camerax.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.leovp.android.exts.circularClose
import com.leovp.android.exts.circularReveal
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.utils.FileDocumentUtil
import com.leovp.camerax.R
import com.leovp.camerax.databinding.FragmentVideoBinding
import com.leovp.camerax.databinding.IncPreviewGridBinding
import com.leovp.camerax.databinding.IncRatioOptionsBinding
import com.leovp.camerax.enums.CameraRatio
import com.leovp.camerax.enums.RecordUiState
import com.leovp.camerax.fragments.base.BaseCameraXFragment
import com.leovp.camerax.listeners.CameraXTouchListener
import com.leovp.camerax.utils.getAspectRatio
import com.leovp.camerax.utils.getAspectRatioString
import com.leovp.camerax.utils.getNameString
import com.leovp.camerax.utils.toggleButton
import com.leovp.kotlin.exts.humanReadableByteCount
import com.leovp.log.LogContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@SuppressLint("RestrictedApi")
class VideoFragment : BaseCameraXFragment<FragmentVideoBinding>() {
    override fun getTagName() = "VideoFragment"

    // https://stackoverflow.com/a/64858848/1685062
    private lateinit var incPreviewGridBinding: IncPreviewGridBinding
    private lateinit var incRatioBinding: IncRatioOptionsBinding

    private var videoOutFile: File? = null
    private var videoOutUri: Uri? = null

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): FragmentVideoBinding {
        val rootBinding = FragmentVideoBinding.inflate(inflater, container, false)
        // https://stackoverflow.com/a/64858848/1685062
        incPreviewGridBinding = IncPreviewGridBinding.bind(rootBinding.root)
        incRatioBinding = IncRatioOptionsBinding.bind(rootBinding.root)
        return rootBinding
    }

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax)
    }

    // Selector showing which flash mode is selected (on, off)
    private var flashMode by Delegates.observable(ImageCapture.FLASH_MODE_OFF) { _, _, new ->
        val flashDrawable =
            if (new == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        binding.btnFlash.setImageResource(flashDrawable)
    }

    private val cameraCapabilities = mutableMapOf<CameraSelector, List<Quality>>()

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null
    private lateinit var recordingState: VideoRecordEvent

    private var selectedRatio = CameraRatio.R16v9
    private var selectedQuality: Quality = DEFAULT_QUALITY

    private var audioEnabled = true

    // Selector showing is flash enabled or not
    private var torchEnabled = false

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    private var enumerationDeferred: Deferred<Unit>? = null

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status) {
            recordingState = event
        }

        updateUI(event)
    }

    private val blinkAnim = AlphaAnimation(0.1f, 1.0f).apply {
        duration = 400
        repeatCount = Animation.INFINITE
        repeatMode = Animation.REVERSE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wait for the views to be properly laid out
        incPreviewGridBinding.viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = incPreviewGridBinding.viewFinder.display.displayId
            // Build UI controls
            updateCameraUi()
            lifecycleScope.launch(Dispatchers.Main) {
                enumerationDeferred?.await()
                enumerationDeferred = null
                // Set up the camera and its use cases
                setUpCamera()
            }
        }
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private suspend fun setUpCamera() {
        configCamera()
        touchListener = object : CameraXTouchListener {
            override fun onStartFocusing(x: Float, y: Float) = binding.focusView.startFocus(x.toInt(), y.toInt())

            override fun onFocusSuccess() = binding.focusView.focusSuccess()

            override fun onFocusFail() = binding.focusView.focusFail()

            override fun onDoubleTap(x: Float, y: Float, zoomRatio: Float) {
                LogContext.log.w(logTag, "Double click at ($x,$y) to zoom to $zoomRatio.")
            }

            override fun onZoomBegin(ratio: Float) {
            }

            override fun onZoom(ratio: Float) {
            }

            override fun onZoomEnd(ratio: Float) {
            }
        }

        // Build and bind the camera use cases
        bindCaptureUseCase()
    }

    /**
     * Main cameraX capture functions.
     *
     * Always bind preview + video capture use case combinations in this sample
     * (VideoCapture can work on its own). The function should always execute on
     * the main thread.
     */
    private fun bindCaptureUseCase(enableUI: Boolean = true) {
        //        showAvailableRatio(incRatioBinding, selectedRatio, incPreviewGridBinding.viewFinder/*, binding.btnRatio*/)

        val rotation = incPreviewGridBinding.viewFinder.display.rotation

        //        // Get screen metrics used to setup camera for full screen resolution
        //        val metrics = requireContext().screenRealResolution
        //        //        val screenAspectRatio = aspectRatio(metrics.width, metrics.height)
        //
        //        val cameraId = if (CameraSelector.DEFAULT_BACK_CAMERA == lensFacing) "0" else "1"
        //        val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        //        val tempSupportedSize: SmartSize? = when (selectedRatio) {
        //            CameraRatio.R16v9 -> characteristics.getCameraSupportedSize().firstOrNull { getRatio(it) == "16:9" }
        //            CameraRatio.R1v1  -> characteristics.getCameraSupportedSize().firstOrNull { getRatio(it) == "1:1" }
        //            CameraRatio.R4v3  -> characteristics.getCameraSupportedSize().firstOrNull { getRatio(it) == "4:3" }
        //            CameraRatio.RFull -> characteristics.getCameraSupportedSize().firstOrNull {
        //                (it.long * 1.0 / it.short).round(1) == (metrics.height * 1.0 / metrics.width).round(1)
        //            }
        //        }
        //        val supportedSize = tempSupportedSize ?: throw IllegalStateException("Unknown camera size $tempSupportedSize")
        //        val targetSize = Size(supportedSize.short, supportedSize.long)

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val qualitySelector =
            QualitySelector.from(
                selectedQuality,
                FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
            )
        LogContext.log.w(
            logTag,
            "cameraSelector=${
                if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    "Front"
                } else {
                    "Back"
                }
            } Selected quality=$selectedQuality"
        )

        incPreviewGridBinding.viewFinder.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val orientation = this@VideoFragment.resources.configuration.orientation
            val ratioString =
                selectedQuality.getAspectRatioString((orientation == Configuration.ORIENTATION_PORTRAIT))
            when (ratioString) {
                "V,9:16" -> updateRatioUI(CameraRatio.R16v9, incPreviewGridBinding.viewFinder)
                "V,3:4" -> updateRatioUI(CameraRatio.R4v3, incPreviewGridBinding.viewFinder)
            }
            dimensionRatio = ratioString
        }

        val hasGrid = prefs.getBoolean(KEY_GRID, false)
        // Show grid after preview view adjusted.
        incPreviewGridBinding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE

        //        val maxPreviewSize = getMaxPreviewSize(cameraSelector)
        //        LogContext.log.w(logTag, "Max preview size=$maxPreviewSize")

        val preview = Preview.Builder()
            // Cannot use both setTargetResolution and setTargetAspectRatio on the same config.
            .setTargetAspectRatio(selectedQuality.getAspectRatio())
            // Set initial target rotation
            .setTargetRotation(rotation)
            // Cannot use both setTargetResolution and setTargetAspectRatio on the same config.
            //            .setTargetResolution(targetSize)
            .build().apply {
                // Attach the viewfinder's surface provider to preview use case
                setSurfaceProvider(incPreviewGridBinding.viewFinder.surfaceProvider)
            }

        // build a recorder, which can:
        //   - record video/audio to MediaStore(only shown here), File, ParcelFileDescriptor
        //   - be used create recording(s) (the recording performs recording)
        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        videoCapture = VideoCapture.withOutput(recorder)

        val camProvider = cameraProvider
        checkNotNull(camProvider) { "Camera initialization failed. Did you call configCamera() method?" }
        try {
            // Must unbind the use-cases before rebinding them
            camProvider.unbindAll()

            camera = camProvider.bindToLifecycle(
                // current lifecycle owner
                viewLifecycleOwner,
                // either front or back facing
                lensFacing,
                // video capture use case
                videoCapture,
                // camera preview use case
                preview
            )

            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            val cameraName =
                if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) "Back" else "Front"
            LogContext.log.i(logTag, "$cameraName camera support flash: $hasFlash")
            binding.btnFlash.visibility = if (hasFlash) View.VISIBLE else View.GONE

            // Call this after [camProvider.bindToLifecycle]
            initCameraGesture(incPreviewGridBinding.viewFinder, camera!!)
        } catch (exc: Exception) {
            // we are on main thread, let's reset the controls on the UI.
            LogContext.log.e(logTag, "Use case binding failed", exc)
            resetUIAndState("bindToLifecycle failed: $exc")
        }

        enableUI(enableUI)
    }

    /**
     * Kick start the video recording
     *   - config Recorder to capture to MediaStoreOutput
     *   - register RecordEvent Listener
     *   - apply audio request from user
     *   - start recording!
     * After this function, user could start/pause/resume/stop recording and application listens
     * to VideoRecordEvent for the current recording status.
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun startRecording(saveInGallery: Boolean = true, baseFolderName: String = BASE_FOLDER_NAME) {
        val outFileName = createFile(outputVideoDirectory, FILENAME, VIDEO_EXTENSION)

        // Configure Recorder and Start recording to the mediaStoreOutput.
        val pendingRecording = if (saveInGallery) {
            // Create MediaStoreOutputOptions for our recorder: resulting our recording!
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DISPLAY_NAME, outFileName.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // As of Android Q
                // File will be saved in /sdcard/Movies/CameraX
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + File.separator + baseFolderName
                )
            }
            LogContext.log.w(logTag, "Save video in gallery: $contentValues")
            val outputOptions = MediaStoreOutputOptions.Builder(
                requireActivity().contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            videoCapture.output.prepareRecording(requireActivity(), outputOptions)
        } else { // Save in app internal folder (Android/data)
            videoOutFile = outFileName
            val outFile = videoOutFile!!
            LogContext.log.i(logTag, "Save video in file: ${outFile.absolutePath}")
            val outputOptions = FileOutputOptions.Builder(outFile).build()
            videoCapture.output.prepareRecording(requireActivity(), outputOptions)
        }

        currentRecording = pendingRecording.apply { if (audioEnabled) withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

        LogContext.log.w(
            logTag,
            "Recording started with audio ${if (audioEnabled) "on" else "off"}..."
        )
    }

    /**
     * Query and cache this platform's camera capabilities, run only once.
     */
    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(requireContext()).await()
                provider.unbindAll()
                for (
                camSelector in arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraSelector.DEFAULT_FRONT_CAMERA
                )
                ) {
                    runCatching {
                        // just get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(camSelector)) {
                            val camera = provider.bindToLifecycle(requireActivity(), camSelector)
                            val supportedQualities = QualitySelector.getSupportedQualities(camera.cameraInfo)
                            val camName =
                                if (camSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "Front" else "Back"
                            LogContext.log.w(
                                logTag,
                                "$camName camera supported qualities=${supportedQualities.map { it.getNameString() }}"
                            )
                            supportedQualities.filter { quality ->
                                listOf(Quality.UHD, Quality.FHD, Quality.HD).contains(quality)
                            }.also {
                                cameraCapabilities[camSelector] = it
                            }
                        }
                    }.onFailure {
                        LogContext.log.e(logTag, "Camera Face $camSelector is not supported")
                    }
                }
            }
        }
    }

    /**
     * Initialize UI. Preview and Capture actions are configured in this function.
     * Note that preview and capture are both initialized either by UI or CameraX callbacks
     * (except the very 1st time upon entering to this fragment in onCreateView()
     */
    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun updateCameraUi() {
        resetSwitchCameraIcon()

        setSwipeCallback(
            left = { navController.navigate(R.id.action_video_fragment_to_camera_fragment) },
            right = { navController.navigate(R.id.action_video_fragment_to_camera_fragment) },
            up = { binding.btnSwitchCamera.performClick() },
            down = { binding.btnSwitchCamera.performClick() }
        )

        // React to user touching the capture button
        binding.btnRecordVideo.apply {
            setOnClickListener {
                if (!this@VideoFragment::recordingState.isInitialized || recordingState is VideoRecordEvent.Finalize) {
                    LogContext.log.i(logTag, "Start recording...")
                    enableUI(false) // Our eventListener will turn on the Recording UI.
                    startRecording()
                } else {
                    if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
                        return@setOnClickListener
                    }
                    currentRecording?.stop()
                    currentRecording = null
                    LogContext.log.i(logTag, "Record stopped!")
                }
            }
            isEnabled = false
        }

        val hasGrid = prefs.getBoolean(KEY_GRID, false)
        binding.btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
        binding.btnGrid.setOnSingleClickListener { toggleGrid() }
        binding.btnMicrophone.setImageResource(
            if (audioEnabled) {
                R.drawable.ic_microphone_on
            } else {
                R.drawable.ic_microphone_off
            }
        )
        binding.btnMicrophone.setOnSingleClickListener { toggleAudio() }
        binding.btnFlash.setOnClickListener { toggleFlash() }

        binding.btnResolution.setOnClickListener { showResolutionLayer() }
        binding.btn4k.setOnClickListener { closeResolutionAndSelect(Quality.UHD) }
        binding.btn1080p.setOnClickListener { closeResolutionAndSelect(Quality.FHD) }
        binding.btn720p.setOnClickListener { closeResolutionAndSelect(Quality.HD) }

        binding.btnRatio.setOnClickListener { showRatioLayer() }
        incRatioBinding.btnRatio4v3.setOnClickListener { closeRatioAndSelect(CameraRatio.R4v3) }
        incRatioBinding.btnRatio16v9.setOnClickListener { closeRatioAndSelect(CameraRatio.R16v9) }
        incRatioBinding.btnRatio1v1.setOnClickListener { closeRatioAndSelect(CameraRatio.R1v1) }
        incRatioBinding.btnRatioFull.setOnClickListener { closeRatioAndSelect(CameraRatio.RFull) }

        updateQualitySelectionUI(selectedQuality)

        binding.btnGallery.setOnClickListener {
            // Display the captured video
            lifecycleScope.launch {
                if (videoOutUri != null) {
                    videoOutUri?.let {
                        val fileRealPath = FileDocumentUtil.getFileRealPath(requireContext(), it)!!
                        val filePath = fileRealPath.substringBeforeLast('/')
                        LogContext.log.i(
                            logTag,
                            "Click Gallery button with uri=$it | real file path=$fileRealPath | real path=$filePath"
                        )
                        navController.navigate(
                            VideoFragmentDirections.actionVideoFragmentToGalleryFragment(filePath)
                        )
                    }
                } else if (videoOutFile != null) {
                    videoOutFile?.let {
                        LogContext.log.i(
                            logTag,
                            "Click Gallery button with file=${it.absolutePath}"
                        )
                        navController.navigate(
                            VideoFragmentDirections.actionVideoFragmentToGalleryFragment(it.absolutePath)
                        )
                    }
                }
            }
        }
    }

    private fun toggleAudio() = binding.btnMicrophone.toggleButton(
        flag = audioEnabled,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_microphone_off,
        secondIcon = R.drawable.ic_microphone_on
    ) { flag ->
        audioEnabled = flag
        LogContext.log.w(logTag, "Enable audio: $audioEnabled")
    }

    /** Turns on or off the grid on the screen */
    private fun toggleGrid() {
        val hasGrid = prefs.getBoolean(KEY_GRID, false)
        LogContext.log.i(logTag, "toggleGrid currentGridFlag=$hasGrid")
        binding.btnGrid.toggleButton(
            flag = hasGrid,
            rotationAngle = 180f,
            firstIcon = R.drawable.ic_grid_off,
            secondIcon = R.drawable.ic_grid_on
        ) { flag ->
            prefs.putBoolean(KEY_GRID, flag)
            incPreviewGridBinding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
        }
    }

    /** Turns on or off the flashlight */
    private fun toggleFlash() = binding.btnFlash.toggleButton(
        flag = flashMode == ImageCapture.FLASH_MODE_ON,
        rotationAngle = 360f,
        firstIcon = R.drawable.ic_flash_off,
        secondIcon = R.drawable.ic_flash_on
    ) { flag ->
        LogContext.log.w(
            logTag,
            "Has Flash: ${camera?.cameraInfo?.hasFlashUnit()} | Turn ${if (flag) "on" else "off"} flash"
        )
        torchEnabled = flag
        flashMode = if (flag) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        camera?.cameraControl?.enableTorch(flag)
    }

    private fun showResolutionLayer() = binding.llResolution.circularReveal(binding.btnResolution)

    private fun closeResolutionAndSelect(quality: Quality) {
        binding.llResolution.circularClose(binding.btnResolution) {
            selectedQuality = quality
            LogContext.log.w(logTag, "Change to ${quality.getNameString()}")
            updateQualitySelectionUI(quality)
            // rebind the use cases to put the new QualitySelection in action.
            enableUI(false)
            bindCaptureUseCase()
        }
    }

    private fun showRatioLayer() = incRatioBinding.llRatioOptions.circularReveal(binding.btnRatio)

    private fun closeRatioAndSelect(ratio: CameraRatio) {
        incRatioBinding.llRatioOptions.circularClose(binding.btnRatio) {
            selectedRatio = ratio
            bindCaptureUseCase()
        }
    }

    private fun updateQualitySelectionUI(quality: Quality) {
        for (q in cameraCapabilities.getValue(lensFacing)) {
            when (q) {
                Quality.UHD -> binding.btn4k.visibility = View.VISIBLE
                Quality.FHD -> binding.btn1080p.visibility = View.VISIBLE
                Quality.HD -> binding.btn720p.visibility = View.VISIBLE
            }
        }

        binding.btnResolution.setImageResource(
            when (quality) {
                Quality.UHD -> R.drawable.ic_resolution_4k
                Quality.FHD -> R.drawable.ic_resolution_1080p
                Quality.HD -> R.drawable.ic_resolution_720p
                else -> throw IllegalArgumentException("Device does not support $quality")
            }
        )
    }

    override fun onStop() {
        if (currentRecording != null && recordingState !is VideoRecordEvent.Finalize) {
            // Stop recording
            currentRecording?.stop()
            currentRecording = null
        }
        camera?.cameraControl?.enableTorch(false)
        super.onStop()
    }

    /**
     * UpdateUI according to CameraX VideoRecordEvent type:
     *   - user starts capture.
     *   - this app disables all UI selections.
     *   - this app enables capture run-time UI (pause/resume/stop).
     *   - user controls recording with run-time UI, eventually tap "stop" to end.
     *   - this app informs CameraX recording to stop with recording.stop() (or recording.close()).
     *   - CameraX notify this app that the recording is indeed stopped, with the Finalize event.
     *   - this app starts VideoViewer fragment to view the captured result.
     */
    private fun updateUI(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Status -> { // Recording in progress
                val s = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                binding.tvRecTime.text =
                    getString(R.string.record_default_time, s / 3600, (s % 3600) / 60, s % 60)
            }

            is VideoRecordEvent.Start -> {
                soundManager.playCameraStartSound()
                showUI(RecordUiState.RECORDING, event.getNameString())
            }

            is VideoRecordEvent.Finalize -> {
                soundManager.playCameraStopSound()
                showUI(RecordUiState.FINALIZED, event.getNameString())
            }

            is VideoRecordEvent.Pause -> {
                binding.icRedDot.clearAnimation()
                binding.btnSwitchCamera.setImageResource(R.drawable.ic_resume)
            }

            is VideoRecordEvent.Resume -> {
                binding.icRedDot.startAnimation(blinkAnim)
                binding.btnSwitchCamera.setImageResource(R.drawable.ic_pause)
            }
        }

        if (event is VideoRecordEvent.Finalize) {
            val state =
                if (event is VideoRecordEvent.Status) recordingState.getNameString() else event.getNameString()
            val stats = event.recordingStats
            val size = stats.numBytesRecorded
            val time = TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
            videoOutUri = event.outputResults.outputUri
            val recordInfo =
                "$state ${size.humanReadableByteCount()}[$size] in ${time}s. Save to: $videoOutUri"
            LogContext.log.w(logTag, "Recorded result: $recordInfo")
        }
    }

    /**
     * Enable/disable UI:
     *    User could select the capture parameters when recording is not in session
     *    Once recording is started, need to disable able UI to avoid conflict.
     */
    private fun enableUI(enable: Boolean) {
        arrayOf(
            binding.btnRecordVideo,
            binding.btnSwitchCamera,
            binding.btnGallery
        ).forEach {
            it.isEnabled = enable
        }
        // Disable the camera switch button if no device to switch
        if (cameraCapabilities.size <= 1) {
            binding.btnSwitchCamera.isEnabled = false
        }
        // Disable the resolution list if no resolution to switch.
        if ((cameraCapabilities[lensFacing]?.size ?: 0) <= 1) {
            binding.btnResolution.isEnabled = false
        }
    }

    /**
     * Initialize UI for recording:
     *  - at recording: hide audio, qualitySelection,change camera UI; enable stop button
     *  - otherwise: show all except the stop button
     */
    @SuppressLint("MissingPermission")
    private fun showUI(state: RecordUiState, status: String = "idle") {
        LogContext.log.i(logTag, "showUI state=$state status=$status")
        binding.let {
            when (state) {
                RecordUiState.IDLE -> {
                    it.btnRecordVideo.setImageResource(R.drawable.ic_start)
                    it.btnGallery.setImageResource(R.drawable.ic_photo)
                    resetSwitchCameraIcon()
                }

                RecordUiState.RECORDING -> {
                    it.tvRecTime.text = getString(R.string.record_default_time, 0, 0, 0)
                    it.llRecLayer.visibility = View.VISIBLE
                    it.icRedDot.startAnimation(blinkAnim)
                    it.btnRecordVideo.apply {
                        setImageResource(R.drawable.ic_stop)
                        isEnabled = true
                    }
                    setSwitchCameraIconToPauseIcon()
                    it.btnGallery.visibility = View.GONE
                    it.btnResolution.visibility = View.GONE
                    it.btnRatio.visibility = View.GONE
                    it.btnMicrophone.visibility = View.GONE
                }

                RecordUiState.FINALIZED -> {
                    it.llRecLayer.visibility = View.GONE
                    it.btnGallery.visibility = View.VISIBLE
                    it.btnResolution.visibility = View.VISIBLE
                    // it.btnRatio.visibility = View.VISIBLE
                    it.btnMicrophone.visibility = View.VISIBLE

                    it.icRedDot.clearAnimation()
                    it.btnRecordVideo.setImageResource(R.drawable.ic_start)
                    resetSwitchCameraIcon()
                    enableUI(true)
                }
            }
        }
    }

    private fun setSwitchCameraIconToPauseIcon() {
        binding.btnSwitchCamera.apply {
            isEnabled = true
            setImageResource(R.drawable.ic_pause)
            setBackgroundResource(R.drawable.ic_outer_circle)
            setPadding(resources.getDimensionPixelSize(R.dimen.spacing_small_large))
            setOnClickListener { doPause() }
        }
    }

    private fun doPause() {
        when (recordingState) {
            is VideoRecordEvent.Start -> currentRecording?.pause()
            is VideoRecordEvent.Pause -> currentRecording?.resume()
            is VideoRecordEvent.Resume -> currentRecording?.pause()
            else -> throw RuntimeException("recordingState in unknown state")
        }
    }

    private fun resetSwitchCameraIcon() {
        binding.btnSwitchCamera.apply {
            setImageResource(R.drawable.ic_switch)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(resources.getDimensionPixelSize(R.dimen.spacing_small))
            setOnClickListener {
                isEnabled = false
                animate().rotationBy(-180f).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        Handler(Looper.getMainLooper()).postDelayed({ enableUI(true) }, 500)
                    }
                })

                switchCameraSelector()
                // Camera device change is in effect instantly:
                //   - reset quality selection
                //   - restart preview
                selectedQuality = DEFAULT_QUALITY
                updateQualitySelectionUI(selectedQuality)
                enableUI(false)
                bindCaptureUseCase(false)
            }
        }
    }

    /**
     * ResetUI (restart):
     *    in case binding failed, let's give it another change for re-try. In future cases
     *    we might fail and user get notified on the status
     */
    private fun resetUIAndState(reason: String) {
        enableUI(true)
        showUI(RecordUiState.IDLE, reason)

        lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
        selectedQuality = DEFAULT_QUALITY
        updateQualitySelectionUI(selectedQuality)
        audioEnabled = false
    }

    companion object {
        private val DEFAULT_QUALITY = Quality.FHD
    }
}
