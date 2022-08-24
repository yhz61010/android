package com.leovp.camera2live.view

import android.hardware.camera2.CameraMetadata
import android.media.MediaActionSound
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ToggleButton
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.leovp.androidbase.utils.media.DeviceSound
import com.leovp.camera2live.Camera2ComponentHelper
import com.leovp.camera2live.sdk.databinding.FragmentCameraViewBinding
import com.leovp.camera2live.utils.OrientationLiveData
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.log.LogContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-6-28 下午5:36
 */
abstract class BaseCamera2Fragment : Fragment() {
    protected var previousLensFacing = CameraMetadata.LENS_FACING_BACK
    private lateinit var switchCameraBtn: ToggleButton
    protected lateinit var switchFlashBtn: ToggleButton

    protected lateinit var camera2Helper: Camera2ComponentHelper
    protected var enableTakePhotoFeature = true
    protected var enableRecordFeature = true
    protected var enableGallery = true

    var backPressListener: BackPressedListener? = null

    /** Where the camera preview is displayed */
    protected lateinit var cameraView: CameraSurfaceView

    private var _binding: FragmentCameraViewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    protected val binding get() = _binding!!

    protected fun getNullableBinding(): FragmentCameraViewBinding? = _binding

    /** Live data listener for changes in the device orientation relative to the camera */
    lateinit var relativeOrientation: OrientationLiveData

    abstract suspend fun getCapturingImage(result: Camera2ComponentHelper.CombinedCaptureResult)
    abstract suspend fun onRecordButtonClick()
    abstract suspend fun onStopRecordButtonClick()
    abstract fun onOpenGallery()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentCameraViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresPermission(android.Manifest.permission.CAMERA)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enableRecordFeature) {
            binding.ivShotRecord.visibility = View.GONE
            binding.ivRecordStop.visibility = View.GONE
            binding.llRecordTime.visibility = View.GONE
        }
        if (!enableTakePhotoFeature) {
            binding.ivShot.visibility = View.GONE
        }
        if (!enableGallery) {
            binding.ivAlbum.visibility = View.GONE
        }
        binding.ivAlbum.setOnSingleClickListener { onOpenGallery() }
        binding.ivBack.setOnSingleClickListener {
            activity?.supportFragmentManager?.popBackStackImmediate()
            backPressListener?.onBackPressed()
        }
        cameraView = binding.cameraSurfaceView
        camera2Helper = Camera2ComponentHelper(requireActivity(), CameraMetadata.LENS_FACING_BACK, view)
        camera2Helper.enableRecordFeature = enableRecordFeature
        camera2Helper.enableTakePhotoFeature = enableTakePhotoFeature
        camera2Helper.enableGallery = enableGallery
        switchFlashBtn = binding.switchFlashBtn
        switchFlashBtn.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) camera2Helper.turnOnFlash() else camera2Helper.turnOffFlash()
        }
        switchCameraBtn = binding.switchFacing
        switchCameraBtn.setOnCheckedChangeListener { btnView: CompoundButton?, _: Boolean ->
            btnView?.isEnabled = false
            binding.ivShot.isEnabled = false
            binding.ivShotRecord.isEnabled = false
//            val rootView = view.findViewById<ViewGroup>(R.id.rootLayout)
//            AnimationUtil.flipAnimatorX(rootView, rootView, 300)
            camera2Helper.switchCamera()
            btnView?.post {
                btnView.isEnabled = true
                binding.ivShot.isEnabled = true
                binding.ivShotRecord.isEnabled = true
            }
        }

        camera2Helper.setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                LogContext.log.w(TAG, "lensFacing=$lensFacing")
                if (CameraMetadata.LENS_FACING_FRONT == lensFacing) {
                    switchFlashBtn.isChecked = false
                    switchFlashBtn.visibility = View.GONE
                } else {
                    switchFlashBtn.visibility = View.VISIBLE
                }
                previousLensFacing = lensFacing
            }
        })

        // Listen to the capture button
        binding.ivShot.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            switchCameraBtn.isEnabled = false
            binding.ivShotRecord.isEnabled = false
            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                val st = SystemClock.elapsedRealtime()
                getCapturingImage(camera2Helper.takePhoto())
                DeviceSound.playShutterClick()
                // Re-enable click listener after photo is taken
                it.post {
                    it.isEnabled = true
                    switchCameraBtn.isEnabled = true
                    binding.ivShotRecord.isEnabled = true
                }
                LogContext.log.d(TAG, "=====> Total click shot button processing cost: ${SystemClock.elapsedRealtime() - st}")
            }
        }

        binding.ivShotRecord.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            switchCameraBtn.isEnabled = false
            binding.ivShot.isEnabled = false

            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                onRecordButtonClick()
                camera2Helper.extraInitializeCameraForRecording()
                camera2Helper.setImageReaderForRecording()
                camera2Helper.setPreviewRepeatingRequest()
                camera2Helper.startRecording()
                // Re-enable click listener after recording is taken
                it.post {
                    it.isEnabled = true
                    switchCameraBtn.isEnabled = false
                    binding.ivShot.isEnabled = false
                }
                DeviceSound.playStartVideoRecording()
            }
        }

        binding.ivRecordStop.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                camera2Helper.stopRecording()
                onStopRecordButtonClick()
                // Re-enable click listener after recording is taken
                it.post {
                    it.isEnabled = true
                    switchCameraBtn.isEnabled = true
                    binding.ivShot.isEnabled = true
                    camera2Helper.initializeCamera(
                        camera2Helper.previewWidth,
                        camera2Helper.previewHeight
                    )
                }
                DeviceSound.playStopVideoRecording()
            }
        }

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), camera2Helper.characteristics).apply {
            observe(viewLifecycleOwner) { orientation ->
                LogContext.log.d(TAG, "Orientation changed: $orientation")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (camera2Helper.isRecording) {
            camera2Helper.stopRecording()
            MediaActionSound().play(MediaActionSound.STOP_VIDEO_RECORDING)
        } else {
            camera2Helper.closeCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        camera2Helper.stopCameraThread()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TAG = BaseCamera2Fragment::class.java.simpleName
    }
}

interface BackPressedListener {
    fun onBackPressed()
}
