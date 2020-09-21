package com.ho1ho.camera2live.view

import android.annotation.SuppressLint
import android.hardware.camera2.CameraMetadata
import android.media.MediaActionSound
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.media.DeviceSound
import com.ho1ho.camera2live.Camera2ComponentHelper
import com.ho1ho.camera2live.R
import com.ho1ho.camera2live.utils.OrientationLiveData
import kotlinx.android.synthetic.main.fragment_camera_view.*
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

    /** Live data listener for changes in the device orientation relative to the camera */
    lateinit var relativeOrientation: OrientationLiveData

    abstract suspend fun getCapturingImage(result: Camera2ComponentHelper.CombinedCaptureResult)
    abstract suspend fun onRecordButtonClick()
    abstract suspend fun onStopRecordButtonClick()
    abstract fun onOpenGallery()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_camera_view, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enableRecordFeature) {
            view.findViewById<View>(R.id.ivShotRecord).visibility = View.GONE
            view.findViewById<View>(R.id.ivRecordStop).visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.llRecordTime).visibility = View.GONE
        }
        if (!enableTakePhotoFeature) {
            view.findViewById<View>(R.id.ivShot).visibility = View.GONE
        }
        if (!enableGallery) {
            view.findViewById<View>(R.id.ivAlbum).visibility = View.GONE
        }
        view.findViewById<View>(R.id.ivAlbum).setOnClickListener { onOpenGallery() }
        view.findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            activity?.supportFragmentManager?.popBackStackImmediate()
            backPressListener?.onBackPressed()
        }
        cameraView = view.findViewById(R.id.cameraSurfaceView)
        camera2Helper = Camera2ComponentHelper(requireActivity(), CameraMetadata.LENS_FACING_BACK, view)
        camera2Helper.enableRecordFeature = enableRecordFeature
        camera2Helper.enableTakePhotoFeature = enableTakePhotoFeature
        camera2Helper.enableGallery = enableGallery
        switchFlashBtn = view.findViewById(R.id.switchFlashBtn)
        switchFlashBtn.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) camera2Helper.turnOnFlash() else camera2Helper.turnOffFlash()
        }
        switchCameraBtn = view.findViewById(R.id.switchFacing)
        switchCameraBtn.setOnCheckedChangeListener { btnView: CompoundButton?, _: Boolean ->
            btnView?.isEnabled = false
            ivShot.isEnabled = false
            ivShotRecord.isEnabled = false
//            val rootView = view.findViewById<ViewGroup>(R.id.rootLayout)
//            AnimationUtil.flipAnimatorX(rootView, rootView, 300)
            camera2Helper.switchCamera()
            btnView?.post {
                btnView.isEnabled = true
                ivShot.isEnabled = true
                ivShotRecord.isEnabled = true
            }
        }

        camera2Helper.setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                LLog.w(TAG, "lensFacing=$lensFacing")
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
        ivShot.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            switchCameraBtn.isEnabled = false
            ivShotRecord.isEnabled = false
            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                val st = SystemClock.elapsedRealtime()
                getCapturingImage(camera2Helper.takePhoto())
                DeviceSound.playShutterClick()
                // Re-enable click listener after photo is taken
                it.post {
                    it.isEnabled = true
                    switchCameraBtn.isEnabled = true
                    ivShotRecord.isEnabled = true
                }
                LLog.d(TAG, "=====> Total click shot button processing cost: ${SystemClock.elapsedRealtime() - st}")
            }
        }

        ivShotRecord.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            switchCameraBtn.isEnabled = false
            ivShot.isEnabled = false

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
                    ivShot.isEnabled = false
                }
                DeviceSound.playStartVideoRecording()
            }
        }

        ivRecordStop.setOnClickListener {
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
                    ivShot.isEnabled = true
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
            observe(viewLifecycleOwner, { orientation ->
                LLog.d(TAG, "Orientation changed: $orientation")
            })
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

    companion object {
        private val TAG = BaseCamera2Fragment::class.java.simpleName
    }
}

interface BackPressedListener {
    fun onBackPressed()
}