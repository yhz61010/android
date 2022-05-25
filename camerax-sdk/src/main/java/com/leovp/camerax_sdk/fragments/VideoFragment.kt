package com.leovp.camerax_sdk.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
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
import androidx.camera.video.*
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
import com.leovp.camerax_sdk.R
import com.leovp.camerax_sdk.databinding.FragmentVideoBinding
import com.leovp.camerax_sdk.fragments.base.BaseCameraXFragment
import com.leovp.camerax_sdk.utils.*
import com.leovp.lib_common_android.exts.circularClose
import com.leovp.lib_common_android.exts.circularReveal
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

@SuppressLint("RestrictedApi")
class VideoFragment : BaseCameraXFragment<FragmentVideoBinding>() {
    override fun getTagName() = "VideoFragment"

    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.getInstance(requireContext()) }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            FragmentVideoBinding.inflate(inflater, container, false)

    // Selector showing which flash mode is selected (on, off)
    private var flashMode by Delegates.observable(ImageCapture.FLASH_MODE_OFF) { _, _, new ->
        val flashDrawable = if (new == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        binding.btnFlash.setImageResource(flashDrawable)
    }

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax)
    }

    private val cameraCapabilities = mutableMapOf<CameraSelector, List<Quality>>()

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null
    private lateinit var recordingState: VideoRecordEvent

    // Camera UI  states and inputs
    enum class UiState {
        IDLE,      // Not recording, all UI controls are active.
        RECORDING, // Camera is recording, only display Pause/Resume & Stop button.
        FINALIZED, // Recording just completes, disable all RECORDING UI controls.
        //        RECOVERY   // For future use.
    }

    private var cameraIndex = 0
    private var qualityIndex = DEFAULT_QUALITY_IDX

    private var audioEnabled = true

    // Selector showing is flash enabled or not
    private var torchEnabled = false

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    private var enumerationDeferred: Deferred<Unit>? = null

    private val blinkAnim = AlphaAnimation(0.1f, 1.0f).apply {
        duration = 400
        repeatCount = Animation.INFINITE
        repeatMode = Animation.REVERSE
    }

    /**
     * Main cameraX capture functions.
     *
     * Always bind preview + video capture use case combinations in this sample
     * (VideoCapture can work on its own). The function should always execute on
     * the main thread.
     */
    private suspend fun bindCaptureUseCase(enableUI: Boolean = true) {
        val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()

        val cameraSelector = getCameraSelector(cameraIndex)
        LogContext.log.w(logTag,
            "cameraSelector=${if (cameraSelector.lensFacing == CameraSelector.LENS_FACING_FRONT) "Front" else "Back"}")

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality: Quality = cameraCapabilities[cameraSelector]!![qualityIndex]
        LogContext.log.w(logTag, "Selected quality=$quality")
        val qualitySelector = QualitySelector.from(quality)

        binding.viewFinder.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val orientation = this@VideoFragment.resources.configuration.orientation
            dimensionRatio = quality.getAspectRatioString(
                quality,
                (orientation == Configuration.ORIENTATION_PORTRAIT)
            )
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(quality.getAspectRatio(quality))
            .build().apply {
                setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        // build a recorder, which can:
        //   - record video/audio to MediaStore(only shown here), File, ParcelFileDescriptor
        //   - be used create recording(s) (the recording performs recording)
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            // Unbind the use-cases before rebinding them.
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner, // current lifecycle owner
                cameraSelector, // either front or back facing
                videoCapture, // video capture use case
                preview // camera preview use case
            )

            val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
            val cameraName = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) "Back" else "Front"
            LogContext.log.w(logTag, "$cameraName camera support flash: $hasFlash")
            binding.btnFlash.visibility = if (hasFlash) View.VISIBLE else View.GONE
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
    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = SimpleDateFormat(FILENAME, Locale.US).format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        currentRecording = videoCapture.output
            .prepareRecording(requireActivity(), mediaStoreOutput)
            .apply { if (audioEnabled) withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

        LogContext.log.w(logTag, "Recording started with audio ${if (audioEnabled) "on" else "off"}...")
    }

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status) {
            recordingState = event
        }

        updateUI(event)

        // FIXME process after stopping
        //        if (event is VideoRecordEvent.Finalize) {
        //            // display the captured video
        //            lifecycleScope.launch {
        //                navController.navigate(
        //                    // FIXME We need Uri not string
        //                    VideoFragmentDirections.actionVideoFragmentToGalleryFragment(event.outputResults.outputUri.path!!)
        //                )
        //            }
        //        }
    }

    /**
     * Retrieve the asked camera's type(lens facing type). In this sample, only 2 types:
     *   idx is even number:  CameraSelector.LENS_FACING_BACK
     *          odd number:   CameraSelector.LENS_FACING_FRONT
     */
    private fun getCameraSelector(idx: Int): CameraSelector {
        if (cameraCapabilities.isEmpty()) {
            LogContext.log.e(logTag, "Error: This device does not have any camera, bailing out")
            requireActivity().finish()
        }
        return if (idx % 2 == 0) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    /**
     * Query and cache this platform's camera capabilities, run only once.
     */
    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(requireContext()).await()

                provider.unbindAll()
                for (camSelector in arrayOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    try {
                        // just get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(camSelector)) {
                            val camera = provider.bindToLifecycle(requireActivity(), camSelector)
                            val supportedQualities = QualitySelector.getSupportedQualities(camera.cameraInfo)
                            val camName = if (camSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "Front" else "Back"
                            LogContext.log.w(logTag,
                                "$camName camera supported qualities=${supportedQualities.map { it.getNameString() }}")
                            supportedQualities.filter { quality ->
                                listOf(Quality.UHD, Quality.FHD, Quality.HD).contains(quality)
                            }.also {
                                cameraCapabilities[camSelector] = it
                            }
                        }
                    } catch (exc: java.lang.Exception) {
                        LogContext.log.e(logTag, "Camera Face $camSelector is not supported")
                    }
                }
            }
        }
    }

    /**
     * One time initialize for CameraFragment (as a part of fragment layout's creation process).
     * This function performs the following:
     *   - initialize but disable all UI controls except the Quality selection.
     *   - set up the Quality selection recycler view.
     *   - bind use cases to a lifecycle camera, enable UI controls.
     */
    private fun initCameraFragment() {
        initializeUI()
        viewLifecycleOwner.lifecycleScope.launch {
            enumerationDeferred?.await()
            enumerationDeferred = null

            bindCaptureUseCase()

            initCameraGesture(binding.viewFinder, camera!!)

            setSwipeCallback(
                left = { navController.navigate(R.id.action_video_fragment_to_camera_fragment) },
                right = { navController.navigate(R.id.action_video_fragment_to_camera_fragment) },
                up = { binding.btnSwitchCamera.performClick() },
                down = { binding.btnSwitchCamera.performClick() }
            )
        }
    }

    /**
     * Initialize UI. Preview and Capture actions are configured in this function.
     * Note that preview and capture are both initialized either by UI or CameraX callbacks
     * (except the very 1st time upon entering to this fragment in onCreateView()
     */
    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun initializeUI() {
        resetSwitchCameraIcon()

        // React to user touching the capture button
        binding.btnRecordVideo.apply {
            setOnClickListener {
                if (!this@VideoFragment::recordingState.isInitialized ||
                    recordingState is VideoRecordEvent.Finalize
                ) {
                    LogContext.log.i(logTag, "Start recording...")
                    enableUI(false)  // Our eventListener will turn on the Recording UI.
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
        binding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE
        binding.btnGrid.setOnSingleClickListener { toggleGrid() }
        binding.btnMicrophone.setImageResource(if (audioEnabled) R.drawable.ic_microphone_on else R.drawable.ic_microphone_off)
        binding.btnMicrophone.setOnSingleClickListener { toggleAudio() }
        binding.btnFlash.setOnClickListener { toggleFlash() }

        binding.btnResolution.setOnClickListener { showResolutionLayer() }
        binding.btn4k.setOnClickListener { closeResolutionAndSelect(Quality.UHD) }
        binding.btn1080p.setOnClickListener { closeResolutionAndSelect(Quality.FHD) }
        binding.btn720p.setOnClickListener { closeResolutionAndSelect(Quality.HD) }
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
            secondIcon = R.drawable.ic_grid_on,
        ) { flag ->
            prefs.putBoolean(KEY_GRID, flag)
            binding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
        }
    }

    /** Turns on or off the flashlight */
    private fun toggleFlash() = binding.btnFlash.toggleButton(
        flag = flashMode == ImageCapture.FLASH_MODE_ON,
        rotationAngle = 360f,
        firstIcon = R.drawable.ic_flash_off,
        secondIcon = R.drawable.ic_flash_on
    ) { flag ->
        LogContext.log.w(logTag,
            "Has Flash: ${camera?.cameraInfo?.hasFlashUnit()} | Turn ${if (flag) "on" else "off"} flash")
        torchEnabled = flag
        flashMode = if (flag) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        camera?.cameraControl?.enableTorch(flag)
    }

    private fun showResolutionLayer() =
            binding.llResolution.circularReveal(binding.btnResolution)

    private fun closeResolutionAndSelect(quality: Quality) {
        binding.llResolution.circularClose(binding.btnResolution) {
            val cameraSelector = getCameraSelector(cameraIndex)
            qualityIndex = cameraCapabilities[cameraSelector]!!.indexOf(quality)
            LogContext.log.w(logTag, "Change to ${quality.getNameString()} index: $qualityIndex")
            binding.btnResolution.setImageResource(
                when (quality) {
                    Quality.UHD -> R.drawable.top_tool_bar_video_resolution_4k
                    Quality.FHD -> R.drawable.top_tool_bar_video_resolution_1080p
                    Quality.HD  -> R.drawable.top_tool_bar_video_resolution_720p
                    else        -> R.drawable.top_tool_bar_video_resolution_1080p
                }
            )
            // rebind the use cases to put the new QualitySelection in action.
            enableUI(false)
            viewLifecycleOwner.lifecycleScope.launch { bindCaptureUseCase() }
        }
    }

    override fun onStop() {
        // Stop recording
        binding.btnRecordVideo.callOnClick()
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
            is VideoRecordEvent.Status   -> { // Recording in progress
                val s = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                binding.tvRecTime.text = getString(R.string.record_default_time, s / 3600, (s % 3600) / 60, s % 60)
            }
            is VideoRecordEvent.Start    -> {
                soundManager.playCameraStartSound()
                showUI(UiState.RECORDING, event.getNameString())
            }
            is VideoRecordEvent.Finalize -> {
                soundManager.playCameraStopSound()
                showUI(UiState.FINALIZED, event.getNameString())
            }
            is VideoRecordEvent.Pause    -> {
                binding.icRedDot.clearAnimation()
                binding.btnSwitchCamera.setImageResource(R.drawable.ic_resume)
            }
            is VideoRecordEvent.Resume   -> {
                binding.icRedDot.startAnimation(blinkAnim)
                binding.btnSwitchCamera.setImageResource(R.drawable.ic_pause)
            }
        }

        //        val state = if (event is VideoRecordEvent.Status) recordingState.getNameString() else event.getNameString()
        //        val stats = event.recordingStats
        //        val size = stats.numBytesRecorded / 1000
        //        val time = TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
        //        var text = "${state}: recorded ${size}KB, in ${time}second"
        //        if (event is VideoRecordEvent.Finalize)
        //            text = "${text}\nFile saved to: ${event.outputResults.outputUri}"
        //
        //        LogContext.log.d(logTag, "recording event: $text")
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
            binding.btnGallery,
        ).forEach {
            it.isEnabled = enable
        }
        // Disable the camera switch button if no device to switch
        if (cameraCapabilities.size <= 1) {
            binding.btnSwitchCamera.isEnabled = false
        }
        // Disable the resolution list if no resolution to switch.
        if ((cameraCapabilities[getCameraSelector(cameraIndex)]?.size ?: 0) <= 1) {
            binding.btnResolution.isEnabled = false
        }
    }

    /**
     * Initialize UI for recording:
     *  - at recording: hide audio, qualitySelection,change camera UI; enable stop button
     *  - otherwise: show all except the stop button
     */
    @SuppressLint("MissingPermission")
    private fun showUI(state: UiState, status: String = "idle") {
        LogContext.log.w(logTag, "showUI state=$state status=$status")
        binding.let {
            when (state) {
                UiState.IDLE      -> {
                    it.btnRecordVideo.setImageResource(R.drawable.ic_start)
                    it.btnGallery.setImageResource(R.drawable.ic_photo)
                    resetSwitchCameraIcon()
                }
                UiState.RECORDING -> {
                    it.tvRecTime.text = getString(R.string.record_default_time, 0, 0, 0)
                    it.llRecLayer.visibility = View.VISIBLE
                    it.icRedDot.startAnimation(blinkAnim)
                    it.btnRecordVideo.apply {
                        setImageResource(R.drawable.ic_stop)
                        isEnabled = true
                    }
                    setSwitchCameraIconToPauseIcon()
                    it.btnGallery.visibility = View.GONE
                }
                UiState.FINALIZED -> {
                    it.llRecLayer.visibility = View.GONE
                    it.icRedDot.clearAnimation()
                    it.btnRecordVideo.setImageResource(R.drawable.ic_start)
                    it.btnGallery.visibility = View.VISIBLE
                    resetSwitchCameraIcon()
                    enableUI(true)
                }
                else              -> {
                    LogContext.log.e(logTag, "Error: showUI($state) is not supported")
                    return
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
            is VideoRecordEvent.Start  -> currentRecording?.pause()
            is VideoRecordEvent.Pause  -> currentRecording?.resume()
            is VideoRecordEvent.Resume -> currentRecording?.pause()
            else                       -> throw IllegalStateException("recordingState in unknown state")
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

                cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
                // Camera device change is in effect instantly:
                //   - reset quality selection
                //   - restart preview
                qualityIndex = DEFAULT_QUALITY_IDX
                enableUI(false)
                viewLifecycleOwner.lifecycleScope.launch {
                    bindCaptureUseCase(false)
                }
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
        showUI(UiState.IDLE, reason)

        cameraIndex = 0
        qualityIndex = DEFAULT_QUALITY_IDX
        audioEnabled = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCameraFragment()
    }

    companion object {
        // Default Quality selection if no input from UI
        const val DEFAULT_QUALITY_IDX = 0 // Back camera
    }
}
