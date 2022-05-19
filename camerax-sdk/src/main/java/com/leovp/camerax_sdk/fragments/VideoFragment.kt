package com.leovp.camerax_sdk.fragments

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.leovp.camerax_sdk.R
import com.leovp.camerax_sdk.databinding.FragmentVideoBinding
import com.leovp.camerax_sdk.fragments.base.BaseCameraXFragment
import com.leovp.camerax_sdk.utils.SharedPrefsManager
import com.leovp.camerax_sdk.utils.getAspectRatio
import com.leovp.camerax_sdk.utils.getAspectRatioString
import com.leovp.camerax_sdk.utils.getNameString
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("RestrictedApi")
class VideoFragment : BaseCameraXFragment<FragmentVideoBinding>() {
    override fun getTagName() = "VideoFragment"

    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.getInstance(requireContext()) }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            FragmentVideoBinding.inflate(inflater, container, false)

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax)
    }

    private val cameraCapabilities = mutableListOf<CameraCapability>()

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null
    private lateinit var recordingState: VideoRecordEvent

    // Camera UI  states and inputs
    enum class UiState {
        IDLE,       // Not recording, all UI controls are active.
        RECORDING,  // Camera is recording, only display Pause/Resume & Stop button.
        FINALIZED,  // Recording just completes, disable all RECORDING UI controls.
        RECOVERY    // For future use.
    }

    private var cameraIndex = 0
    private var qualityIndex = DEFAULT_QUALITY_IDX
    private var audioEnabled = false

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    private var enumerationDeferred: Deferred<Unit>? = null

    // main cameraX capture functions
    /**
     *   Always bind preview + video capture use case combinations in this sample
     *   (VideoCapture can work on its own). The function should always execute on
     *   the main thread.
     */
    private suspend fun bindCaptureUsecase() {
        val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()

        val cameraSelector = getCameraSelector(cameraIndex)

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality: Quality = cameraCapabilities[cameraIndex].qualities[qualityIndex]
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
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                videoCapture,
                preview
            )
        } catch (exc: Exception) {
            // we are on main thread, let's reset the controls on the UI.
            LogContext.log.e(logTag, "Use case binding failed", exc)
            resetUIandState("bindToLifecycle failed: $exc")
        }
        enableUI(true)
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

        LogContext.log.i(logTag, "Recording started")
    }

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status)
            recordingState = event

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
        if (cameraCapabilities.size == 0) {
            LogContext.log.i(logTag, "Error: This device does not have any camera, bailing out")
            requireActivity().finish()
        }
        return (cameraCapabilities[idx % cameraCapabilities.size].camSelector)
    }

    data class CameraCapability(val camSelector: CameraSelector, val qualities: List<Quality>)

    /**
     * Query and cache this platform's camera capabilities, run only once.
     */
    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(requireContext()).await()

                provider.unbindAll()
                for (camSelector in arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraSelector.DEFAULT_FRONT_CAMERA
                )) {
                    try {
                        // just get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(camSelector)) {
                            camera = provider.bindToLifecycle(requireActivity(), camSelector)
                            QualitySelector
                                .getSupportedQualities(camera!!.cameraInfo)
                                .filter { quality ->
                                    listOf(Quality.UHD,
                                        Quality.FHD,
                                        Quality.HD,
                                        Quality.SD)
                                        .contains(quality)
                                }.also {
                                    cameraCapabilities.add(CameraCapability(camSelector, it))
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
            if (enumerationDeferred != null) {
                enumerationDeferred!!.await()
                enumerationDeferred = null
            }
            initializeQualitySectionsUI()

            bindCaptureUsecase()
        }
    }

    /**
     * Initialize UI. Preview and Capture actions are configured in this function.
     * Note that preview and capture are both initialized either by UI or CameraX callbacks
     * (except the very 1st time upon entering to this fragment in onCreateView()
     */
    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun initializeUI() {
        binding.btnSwitchCamera.apply {
            setOnClickListener {
                cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
                // camera device change is in effect instantly:
                //   - reset quality selection
                //   - restart preview
                qualityIndex = DEFAULT_QUALITY_IDX
                initializeQualitySectionsUI()
                enableUI(false)
                viewLifecycleOwner.lifecycleScope.launch {
                    bindCaptureUsecase()
                }
            }
            isEnabled = false
        }

        // FIXME add me
        // audioEnabled by default is disabled.
        //        captureViewBinding.audioSelection.isChecked = audioEnabled
        //        captureViewBinding.audioSelection.setOnClickListener {
        //            audioEnabled = captureViewBinding.audioSelection.isChecked
        //        }

        // React to user touching the capture button
        binding.btnRecordVideo.apply {
            setOnClickListener { doStartRecording() }
            isEnabled = false
        }

        // FIXME add me
        //        binding.stopButton.apply {
        //            setOnClickListener {
        //                // stopping: hide it after getting a click before we go to viewing fragment
        //                captureViewBinding.stopButton.visibility = View.INVISIBLE
        //                if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
        //                    return@setOnClickListener
        //                }
        //
        //                val recording = currentRecording
        //                if (recording != null) {
        //                    recording.stop()
        //                    currentRecording = null
        //                }
        //                captureViewBinding.captureButton.setImageResource(R.drawable.ic_start)
        //            }
        //            // ensure the stop button is initialized disabled & invisible
        //            visibility = View.INVISIBLE
        //            isEnabled = false
        //        }

        initCameraGesture(binding.viewFinder, camera!!)

        setSwipeCallback(right = { navController.navigate(R.id.action_video_fragment_to_camera_fragment) })
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun doStartRecording() {
        LogContext.log.i(logTag, "doStartRecording")
        if (!this@VideoFragment::recordingState.isInitialized ||
            recordingState is VideoRecordEvent.Finalize
        ) {
            enableUI(false)  // Our eventListener will turn on the Recording UI.
            startRecording()
        } else {
            // FIXME How to enter here?
            when (recordingState) {
                is VideoRecordEvent.Start  -> {
                    currentRecording?.pause()
                    // FIXME add me
                    //                            binding.stopButton.visibility = View.VISIBLE
                }
                is VideoRecordEvent.Pause  -> currentRecording?.resume()
                is VideoRecordEvent.Resume -> currentRecording?.pause()
                else                       -> throw IllegalStateException("recordingState in unknown state")
            }
        }
    }

    private fun doStopRecording() {
        if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
            return
        }

        val recording = currentRecording
        if (recording != null) {
            recording.stop()
            currentRecording = null
        }
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
        val state = if (event is VideoRecordEvent.Status) recordingState.getNameString() else event.getNameString()
        when (event) {
            is VideoRecordEvent.Status   -> {
                // placeholder: we update the UI with new status after this when() block,
                // nothing needs to do here.
            }
            is VideoRecordEvent.Start    -> {
                showUI(UiState.RECORDING, event.getNameString())
            }
            is VideoRecordEvent.Finalize -> {
                showUI(UiState.FINALIZED, event.getNameString())
            }
            is VideoRecordEvent.Pause    -> {
                binding.btnGallery.setImageResource(R.drawable.ic_resume)
            }
            is VideoRecordEvent.Resume   -> {
                binding.btnGallery.setImageResource(R.drawable.ic_pause)
            }
        }

        val stats = event.recordingStats
        val size = stats.numBytesRecorded / 1000
        val time = TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
        var text = "${state}: recorded ${size}KB, in ${time}second"
        if (event is VideoRecordEvent.Finalize)
            text = "${text}\nFile saved to: ${event.outputResults.outputUri}"

        LogContext.log.w(logTag, "recording event: $text")
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
        // disable the camera button if no device to switch
        if (cameraCapabilities.size <= 1) {
            binding.btnRecordVideo.isEnabled = false
        }
        // TODO
        // disable the resolution list if no resolution to switch
        //        if (cameraCapabilities[cameraIndex].qualities.size <= 1) {
        //            captureViewBinding.qualitySelection.apply { isEnabled = false }
        //        }
    }

    /**
     * initialize UI for recording:
     *  - at recording: hide audio, qualitySelection,change camera UI; enable stop button
     *  - otherwise: show all except the stop button
     */
    @SuppressLint("MissingPermission")
    private fun showUI(state: UiState, status: String = "idle") {
        binding.let {
            when (state) {
                UiState.IDLE      -> {
                    it.btnRecordVideo.setImageResource(R.drawable.ic_start)
                    it.btnGallery.setImageResource(R.drawable.ic_photo)
                    //                    it.audioSelection.visibility = View.VISIBLE
                    //                    it.qualitySelection.visibility = View.VISIBLE
                }
                UiState.RECORDING -> {
                    it.btnRecordVideo.apply {
                        setImageResource(R.drawable.ic_stop)
                        setOnClickListener { doStopRecording() }
                        isEnabled = true
                    }

                    it.btnGallery.setImageResource(R.drawable.ic_pause)
                    //                    it.audioSelection.visibility = View.INVISIBLE
                    //                    it.qualitySelection.visibility = View.INVISIBLE
                }
                UiState.FINALIZED -> {
                    it.btnRecordVideo.apply {
                        setImageResource(R.drawable.ic_start)
                        setOnClickListener { doStartRecording() }
                    }
                    it.btnGallery.setImageResource(R.drawable.ic_photo)
                }
                else              -> {
                    val errorMsg = "Error: showUI($state) is not supported"
                    LogContext.log.e(logTag, errorMsg)
                    return
                }
            }
        }
    }

    /**
     * ResetUI (restart):
     *    in case binding failed, let's give it another change for re-try. In future cases
     *    we might fail and user get notified on the status
     */
    private fun resetUIandState(reason: String) {
        enableUI(true)
        showUI(UiState.IDLE, reason)

        cameraIndex = 0
        qualityIndex = DEFAULT_QUALITY_IDX
        audioEnabled = false
        // FIXME add me
        //        captureViewBinding.audioSelection.isChecked = audioEnabled
        initializeQualitySectionsUI()
    }

    /**
     *  initializeQualitySectionsUI():
     *    Populate a RecyclerView to display camera capabilities:
     *       - one front facing
     *       - one back facing
     *    User selection is saved to qualityIndex, will be used
     *    in the bindCaptureUsecase().
     */
    private fun initializeQualitySectionsUI() {
        val selectorStrings = cameraCapabilities[cameraIndex].qualities.map {
            it.getNameString()
        }
        // TODO
        // create the adapter to Quality selection RecyclerView
        //        captureViewBinding.qualitySelection.apply {
        //            layoutManager = LinearLayoutManager(context)
        //            adapter = GenericListAdapter(
        //                selectorStrings,
        //                itemLayoutId = R.layout.video_quality_item
        //            ) { holderView, qcString, position ->
        //
        //                holderView.apply {
        //                    findViewById<TextView>(R.id.qualityTextView)?.text = qcString
        //                    // select the default quality selector
        //                    isSelected = (position == qualityIndex)
        //                }
        //
        //                holderView.setOnClickListener { view ->
        //                    if (qualityIndex == position) return@setOnClickListener
        //
        //                    captureViewBinding.qualitySelection.let {
        //                        // deselect the previous selection on UI.
        //                        it.findViewHolderForAdapterPosition(qualityIndex)
        //                            ?.itemView
        //                            ?.isSelected = false
        //                    }
        //                    // turn on the new selection on UI.
        //                    view.isSelected = true
        //                    qualityIndex = position
        //
        //                    // rebind the use cases to put the new QualitySelection in action.
        //                    enableUI(false)
        //                    viewLifecycleOwner.lifecycleScope.launch {
        //                        bindCaptureUsecase()
        //                    }
        //                }
        //            }
        //            isEnabled = false
        //        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCameraFragment()
    }

    companion object {
        // default Quality selection if no input from UI
        const val DEFAULT_QUALITY_IDX = 0
    }
}
